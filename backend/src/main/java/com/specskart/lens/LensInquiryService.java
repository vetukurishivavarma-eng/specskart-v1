package com.specskart.lens;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import com.specskart.shared.ApiException;
import com.specskart.shared.PhoneNumbers;
import com.specskart.shared.TokenGenerator;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The client's lens-only funnel: pick lens type + blue block, verify the WhatsApp number
 * with a magic link, then (once verified) fill the prescription and get a price. The
 * verified number + lens type + blue block are saved the moment verification succeeds —
 * that alone is enough for {@link LensInquiryFollowUpJob} to chase the lead even if the
 * Rx form is never finished.
 */
@Service
public class LensInquiryService {

    private static final Logger log = LoggerFactory.getLogger(LensInquiryService.class);

    private final LensInquiryRepository inquiries;
    private final LeadService leadService;
    private final WhatsAppProvider whatsapp;
    private final TokenGenerator tokens;
    private final AppProperties props;

    public LensInquiryService(LensInquiryRepository inquiries, LeadService leadService,
                              WhatsAppProvider whatsapp, TokenGenerator tokens, AppProperties props) {
        this.inquiries = inquiries;
        this.leadService = leadService;
        this.whatsapp = whatsapp;
        this.tokens = tokens;
        this.props = props;
    }

    @Transactional
    public UUID start(String phone, String lensType, boolean blueBlock) {
        String waId = PhoneNumbers.normalize(phone, props.whatsapp().defaultCountryCode());
        if (waId == null || waId.length() < 8) {
            throw ApiException.badRequest("BAD_NUMBER", "Enter a valid WhatsApp number.");
        }
        if (!"CLEAR".equals(lensType) && !"PHOTOCHROMATIC".equals(lensType)) {
            throw ApiException.badRequest("BAD_LENS_TYPE", "Choose a lens type first.");
        }
        LensInquiry q = new LensInquiry();
        q.setPhoneRaw(phone);
        q.setWaId(waId);
        q.setLensType(lensType);
        q.setBlueBlock(blueBlock);
        String raw = tokens.newToken();
        q.setVerifyTokenHash(tokens.hash(raw));
        inquiries.save(q);

        String link = props.frontendBaseUrl() + "/lens/verify/" + raw;
        try {
            if (props.whatsapp().lensVerifyConfigured()) {
                whatsapp.sendTemplate(waId, props.whatsapp().lensVerifyTemplate(),
                        props.whatsapp().followUpTemplateLang(), List.of(link));
            } else {
                whatsapp.sendText(waId, "Tap to verify your number and continue configuring your lenses:\n" + link);
            }
        } catch (Exception e) {
            log.warn("lens verification send failed for inquiry {}: {}", q.getId(), e.getMessage());
        }
        return q.getId();
    }

    @Transactional
    public boolean verify(String rawToken) {
        LensInquiry q = inquiries.findByVerifyTokenHash(tokens.hash(rawToken)).orElse(null);
        if (q == null) return false;
        if (!q.isPhoneVerified()) {
            q.setPhoneVerifiedAt(Instant.now());
            q.setStatus("VERIFIED");
            Lead lead = leadService.onWebOrder(q.getWaId(), null);
            if (lead != null) q.setLeadId(lead.getId());
            inquiries.save(q);
            log.info("lens inquiry {} verified", q.getId());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public LensDtos.InquiryView status(UUID id) {
        return view(get(id));
    }

    @Transactional
    public LensDtos.InquiryView update(UUID id, LensDtos.UpdateDetails d) {
        LensInquiry q = requireVerified(get(id));
        if (d.customerName() != null) q.setCustomerName(d.customerName());
        if (d.age() != null) q.setAge(d.age());
        if (d.gender() != null) q.setGender(d.gender());
        if (d.sphRight() != null) q.setSphRight(d.sphRight());
        if (d.sphLeft() != null) q.setSphLeft(d.sphLeft());
        if (d.cylRight() != null) q.setCylRight(d.cylRight());
        if (d.cylLeft() != null) q.setCylLeft(d.cylLeft());
        if (d.axisRight() != null) q.setAxisRight(d.axisRight());
        if (d.axisLeft() != null) q.setAxisLeft(d.axisLeft());
        if (d.addPower() != null) q.setAddPower(d.addPower());
        if (d.lensStructure() != null) q.setLensStructure(d.lensStructure());
        q.setSpecialAxis(computeSpecialAxis(q));
        return view(inquiries.save(q));
    }

    @Transactional
    public LensDtos.InquiryView quote(UUID id) {
        LensInquiry q = requireVerified(get(id));
        q.setPriceMinor(LensPricing.quote(q));
        q.setStatus("PRICED");
        return view(inquiries.save(q));
    }

    @Transactional
    public LensDtos.InquiryView submit(UUID id) {
        LensInquiry q = requireVerified(get(id));
        if (q.getPriceMinor() == null) q.setPriceMinor(LensPricing.quote(q));
        q.setStatus("SUBMITTED");
        inquiries.save(q);
        alertStaff(q);
        return view(q);
    }

    private void alertStaff(LensInquiry q) {
        List<String> staff = props.whatsapp().staffNumbers();
        if (staff.isEmpty()) return;
        String msg = "👓 New lens order — " + q.getLensType()
                + (q.isBlueBlock() ? " + blue block" : "")
                + (q.isSpecialAxis() ? "\n⚠ Special axis — needs a manual check, not a stock lens" : "")
                + "\nCustomer: " + (q.getCustomerName() != null ? q.getCustomerName() : "—")
                + "\nWhatsApp: " + q.getWaId();
        for (String to : staff) {
            try { whatsapp.sendText(to.trim(), msg); } catch (Exception e) { log.warn("lens staff alert failed: {}", e.getMessage()); }
        }
    }

    /** The client's own rule: axis away from the 90°/180° bands (±10°) needs a special,
     *  non-stock lens. Only meaningful when that eye actually has a cylindrical correction. */
    private static boolean computeSpecialAxis(LensInquiry q) {
        return isSpecial(q.getCylRight(), q.getAxisRight()) || isSpecial(q.getCylLeft(), q.getAxisLeft());
    }

    private static boolean isSpecial(java.math.BigDecimal cyl, Integer axis) {
        if (cyl == null || cyl.signum() == 0 || axis == null) return false;
        int a = ((axis % 180) + 180) % 180;
        boolean nearNinety = Math.abs(a - 90) <= 10;
        boolean nearZeroOrOneEighty = a <= 10 || a >= 170;
        return !(nearNinety || nearZeroOrOneEighty);
    }

    private LensInquiry get(UUID id) {
        return inquiries.findById(id).orElseThrow(() -> ApiException.notFound("INQUIRY_NOT_FOUND", "No such inquiry."));
    }

    private LensInquiry requireVerified(LensInquiry q) {
        if (!q.isPhoneVerified()) {
            throw ApiException.badRequest("NOT_VERIFIED", "Please verify your WhatsApp number first.");
        }
        return q;
    }

    private static LensDtos.InquiryView view(LensInquiry q) {
        return new LensDtos.InquiryView(q.getId(), q.getStatus(), q.isPhoneVerified(),
                q.getLensType(), q.isBlueBlock(),
                q.getCustomerName(), q.getAge(), q.getGender(),
                q.getSphRight(), q.getSphLeft(), q.getCylRight(), q.getCylLeft(),
                q.getAxisRight(), q.getAxisLeft(), q.getAddPower(), q.getLensStructure(),
                q.isSpecialAxis(), q.getPriceMinor(), q.getCurrency());
    }
}
