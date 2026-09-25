package com.specskart.lens;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoIssuer;
import com.specskart.config.AppProperties;
import com.specskart.lead.FollowUpState;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadService;
import com.specskart.order.ReviewCaptureService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A few days after lenses are handed over, one WhatsApp: check the fit is right, hand over the
 * referral code, and leave a comeback discount. The frames storefront has had this since V6
 * ({@link com.specskart.order.PostPurchaseJob}); the lens funnel — the one the client actually
 * has switched on — ended at "delivered" and said nothing more until the yearly recall.
 *
 * <p>It also asks for a star rating. V43 gave {@code Review} a lensInquiryId so a lens sale has
 * somewhere to record one; a bare "1"-"5" reply is picked up by
 * {@link com.specskart.order.ReviewCaptureService} exactly as it is for a frame order.
 */
@Component
class LensPostPurchaseJob {

    private static final Logger log = LoggerFactory.getLogger(LensPostPurchaseJob.class);
    private static final int DELAY_DAYS = 3;
    /** Walk-ins included: someone billed at the counter is still a customer worth thanking. */
    private static final String SOLD = "SOLD";

    private final LensInquiryRepository inquiries;
    private final LeadRepository leads;
    private final LeadService leadService;
    private final PromoIssuer promoIssuer;
    private final WhatsAppProvider whatsapp;
    private final AnalyticsService analytics;
    private final AppProperties props;
    private final ReviewCaptureService reviewCapture;

    LensPostPurchaseJob(LensInquiryRepository inquiries, LeadRepository leads, LeadService leadService,
                        PromoIssuer promoIssuer, WhatsAppProvider whatsapp, AnalyticsService analytics,
                        AppProperties props, ReviewCaptureService reviewCapture) {
        this.reviewCapture = reviewCapture;
        this.inquiries = inquiries;
        this.leads = leads;
        this.leadService = leadService;
        this.promoIssuer = promoIssuer;
        this.whatsapp = whatsapp;
        this.analytics = analytics;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT7M")
    @Transactional
    public void run() {
        // Shares the nurture master switch — one "stop messaging my customers" lever, not three.
        if (!props.whatsapp().nurtureOn()) return;

        Instant cutoff = Instant.now().minus(DELAY_DAYS, ChronoUnit.DAYS);
        var due = inquiries.findTop50ByStatusAndPostPurchaseAtIsNullAndLeadIdIsNotNullAndUpdatedAtBefore(SOLD, cutoff);
        int sent = 0;
        for (LensInquiry q : due) {
            // Marked first, and regardless of the send: a message we can't deliver today is not
            // worth retrying every hour forever, and it must never go out twice.
            q.setPostPurchaseAt(Instant.now());
            inquiries.save(q);
            try {
                if (thank(q)) sent++;
            } catch (Exception e) {
                log.warn("lens post-purchase failed for inquiry {}: {}", q.getId(), e.getMessage());
            }
        }
        if (sent > 0) log.info("lens post-purchase: thanked {}", sent);
    }

    private boolean thank(LensInquiry q) {
        Lead lead = leads.findById(q.getLeadId()).orElse(null);
        // Same opt-out the nurture sequence and the recall respect — one STOP stops everything.
        if (lead == null || lead.getFollowUpState() == FollowUpState.OPTED_OUT) return false;
        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        if (waId == null || waId.isBlank()) return false;

        PromoCode promo = promoIssuer.forRecall(lead.getId());
        String referral = leadService.ensureReferralCode(lead.getId());
        // Armed before the send: a "1"-"5" reply only counts as a rating once we've asked.
        reviewCapture.markPendingLens(lead.getId(), q.getId());
        // Three days on, the customer's 24h window is long shut and Meta drops free text
        // (#131047). The approved template the frames post-purchase uses -- {{1}} name,
        // {{2}} promo code, {{3}} referral code -- gets through; the plain text stays for dev
        // and an unset template.
        if (props.whatsapp().postPurchaseConfigured()) {
            whatsapp.sendTemplate(waId, props.whatsapp().postPurchaseTemplate(),
                    props.whatsapp().followUpTemplateLang(), List.of(firstName(lead, q), promo.getCode(), referral));
        } else {
            whatsapp.sendText(waId, message(firstName(lead, q), promo, referral));
        }
        analytics.record(LeadEventType.WHATSAPP_FOLLOW_UP_SENT, lead.getId(), null);
        return true;
    }

    private String message(String hi, PromoCode promo, String referral) {
        return "Hi " + hi + " 👋 How are the new lenses treating you?"
                + "\n\nIf anything feels off — blurry edges, headaches, a frame that slips — reply here"
                + " and we'll sort it. Adjustments are free."
                + "\n\n⭐ How would you rate them? Reply with a number from 1 to 5."
                + "\n\n🎁 " + promo.getDiscountValue() + "% off your next pair with code *"
                + promo.getCode() + "*"
                + "\n👥 Share code *" + referral + "* — your friend gets a discount and so do you."
                + "\n\nReply STOP to opt out.";
    }

    /** The delivery name is often the only name a lens customer ever gave us. */
    private static String firstName(Lead lead, LensInquiry q) {
        for (String candidate : new String[]{lead.getName(), q.getCustomerName(), q.getDeliveryName()}) {
            if (candidate != null && !candidate.isBlank()) return candidate.trim().split("\\s+")[0];
        }
        return "there";
    }
}
