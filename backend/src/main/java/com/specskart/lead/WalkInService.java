package com.specskart.lead;

import com.specskart.config.AppProperties;
import com.specskart.shared.ApiException;
import com.specskart.shared.PhoneNumbers;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walk-in customer sign-up. The till shows a wa.me QR with a prefilled message carrying a short
 * code; the customer sends it from their own phone. Receiving that message is the verification
 * (only the number's owner can send from it) and the wording is their consent to offers — no OTP
 * template, no SMS, and it opens a free 24h window for the welcome reply.
 */
@Service
public class WalkInService {

    private static final Logger log = LoggerFactory.getLogger(WalkInService.class);
    static final Duration CODE_TTL = Duration.ofMinutes(30);
    private static final Pattern CODE = Pattern.compile("\\bcode[:\\s]*([A-Z2-9]{6})\\b", Pattern.CASE_INSENSITIVE);
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no I,L,O,0,1
    private static final SecureRandom RNG = new SecureRandom();

    public record Started(UUID id, String code, String waLink, String businessNumber, Instant expiresAt) {}

    public record Status(UUID id, UUID storeId, boolean verified, String customerName, String whatsappNumber) {}

    private final WalkInVerificationRepository verifications;
    private final LeadRepository leads;
    private final WhatsAppProvider whatsapp;
    private final AppProperties props;

    public WalkInService(WalkInVerificationRepository verifications, LeadRepository leads,
                         WhatsAppProvider whatsapp, AppProperties props) {
        this.verifications = verifications;
        this.leads = leads;
        this.whatsapp = whatsapp;
        this.props = props;
    }

    @Transactional
    public Started start(UUID storeId, String customerName, UUID staffUserId) {
        String business = PhoneNumbers.normalize(props.businessWhatsappNumber(), props.whatsapp().defaultCountryCode());
        if (business == null || business.replace("0", "").length() < 4) {
            throw ApiException.badRequest("NO_BUSINESS_NUMBER",
                    "The shop's WhatsApp number isn't set on the server (SPECSKART_BUSINESS_WA_NUMBER).");
        }
        WalkInVerification v = new WalkInVerification();
        v.setCode(freshCode());
        v.setStoreId(storeId);
        v.setStaffUserId(staffUserId);
        v.setCustomerName(customerName == null || customerName.isBlank() ? null : customerName.trim());
        verifications.save(v);

        String msg = "Hi " + props.storeName() + "! Please save my number for offers & updates. Code: " + v.getCode();
        String link = "https://wa.me/" + business + "?text=" + URLEncoder.encode(msg, StandardCharsets.UTF_8).replace("+", "%20");
        return new Started(v.getId(), v.getCode(), link, "+" + business, Instant.now().plus(CODE_TTL));
    }

    @Transactional(readOnly = true)
    public Status status(UUID id) {
        WalkInVerification v = verifications.findById(id)
                .orElseThrow(() -> ApiException.notFound("WALK_IN_NOT_FOUND", "No such sign-up."));
        Lead lead = v.getLeadId() == null ? null : leads.findById(v.getLeadId()).orElse(null);
        return new Status(v.getId(), v.getStoreId(), v.getVerifiedAt() != null,
                lead != null && lead.getName() != null ? lead.getName() : v.getCustomerName(),
                lead == null ? null : "+" + lead.getWhatsappWaId());
    }

    /** Inbound WhatsApp: if the message carries a live walk-in code, verify + opt the sender in
     *  and confirm. Returns true when it consumed the message (the bot shouldn't also reply). */
    @Transactional
    public boolean tryVerify(Lead lead, String text) {
        if (text == null) return false;
        Matcher m = CODE.matcher(text);
        if (!m.find()) return false;
        WalkInVerification v = verifications.findByCode(m.group(1).toUpperCase(Locale.ROOT)).orElse(null);
        if (v == null || v.getVerifiedAt() != null || v.getCreatedAt().plus(CODE_TTL).isBefore(Instant.now())) {
            return false;
        }
        v.setVerifiedAt(Instant.now());
        v.setLeadId(lead.getId());
        verifications.save(v);

        // staff typed the real name; a WhatsApp profile name is often a nickname
        if (v.getCustomerName() != null) lead.setName(v.getCustomerName());
        // brand-new contact (first message arrived after the till made the code) = acquired in-store
        if (lead.getFirstContactAt() != null && !lead.getFirstContactAt().isBefore(v.getCreatedAt())) {
            lead.setAcquisitionSource(AcquisitionSource.WALK_IN);
        }
        if (lead.getHomeStoreId() == null) lead.setHomeStoreId(v.getStoreId());
        lead.setMarketingOptInAt(Instant.now());
        if (lead.getFollowUpState() == FollowUpState.OPTED_OUT) lead.setFollowUpState(null); // fresh, explicit consent
        leads.save(lead);

        String hi = lead.getName() == null || lead.getName().isBlank() ? "" : " " + lead.getName().trim().split("\\s+")[0];
        try {
            whatsapp.sendText(lead.getWhatsappWaId(), "Thanks" + hi + "! ✅ Your number is saved with " + props.storeName()
                    + ". We'll send you new arrivals, offers and your order updates right here.\nReply STOP any time to opt out.");
        } catch (Exception e) {
            log.warn("walk-in welcome to lead {} failed: {}", lead.getId(), e.getMessage());
        }
        log.info("walk-in {} verified as lead {}", v.getCode(), lead.getId());
        return true;
    }

    private String freshCode() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
            if (!verifications.existsByCode(sb.toString())) return sb.toString();
        }
    }
}
