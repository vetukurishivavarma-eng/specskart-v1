package com.specskart.lead;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoIssuer;
import com.specskart.config.AppProperties;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The yearly "time for an eye test" nudge to customers who already bought — the other half of the
 * automated lifecycle from the nurture sequence, which only chases people who never converted.
 *
 * <p>Sent a year (configurable) after a delivered order, and again a year after that, carrying a
 * two-week discount code so the nudge is worth opening. STOP still opts them out for good: the
 * query skips OPTED_OUT leads, so the same reply that stops nurture stops this too.
 */
@Service
public class EyeTestRecallService {

    private static final Logger log = LoggerFactory.getLogger(EyeTestRecallService.class);

    private final LeadRepository leads;
    private final PromoIssuer promoIssuer;
    private final WhatsAppProvider whatsapp;
    private final AnalyticsService analytics;
    private final AppProperties props;

    public EyeTestRecallService(LeadRepository leads, PromoIssuer promoIssuer, WhatsAppProvider whatsapp,
                                AnalyticsService analytics, AppProperties props) {
        this.leads = leads;
        this.promoIssuer = promoIssuer;
        this.whatsapp = whatsapp;
        this.analytics = analytics;
        this.props = props;
    }

    /**
     * Nudge one customer. Marks them recalled whether or not the send lands: a WhatsApp we can't
     * deliver today is not worth retrying every hour for a year, and the next recall is due in
     * another year regardless. The failure is logged so a broken template is still visible.
     */
    @Transactional
    public void recall(UUID leadId) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null) return;
        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        if (waId == null || waId.isBlank()) return;

        int months = props.whatsapp().recallMonths();
        String hi = firstName(lead);
        PromoCode promo = promoIssuer.forRecall(lead.getId());
        String offer = promo.getDiscountValue() + "% off your next pair with code " + promo.getCode()
                + " — valid for two weeks";
        String shop = props.frontendBaseUrl() + "/store";

        lead.setEyeTestRecalledAt(Instant.now());
        leads.save(lead);

        try {
            if (props.whatsapp().recallConfigured()) {
                whatsapp.sendTemplate(waId, props.whatsapp().recallTemplate(),
                        props.whatsapp().followUpTemplateLang(),
                        List.of(hi, String.valueOf(months), offer, shop));
            } else {
                whatsapp.sendText(waId, plainMessage(hi, months, offer, shop));
            }
            analytics.record(LeadEventType.EYE_TEST_RECALL_SENT, lead.getId(), null);
            log.info("eye-test recall sent to lead {}", lead.getId());
        } catch (Exception e) {
            // Same trap as everywhere else here: without an approved template this only reaches
            // someone with an open 24h window, and a year-old customer never has one.
            log.warn("eye-test recall to lead {} failed ({}): {}", lead.getId(),
                    props.whatsapp().recallConfigured() ? "template " + props.whatsapp().recallTemplate() : "plain text",
                    e.getMessage());
        }
    }

    static String plainMessage(String hi, int months, String offer, String shop) {
        return "Hi " + hi + " 👋 It's been about " + months + " months since your last pair from Specskart."
                + "\n\nEyesight drifts — an eye test is the cheapest way to know whether your current"
                + " lenses are still right for you. Book a free check at any Specskart shop, or reply here"
                + " and we'll sort it."
                + "\n\n🎁 " + offer
                + "\n\nBrowse what's new: " + shop
                + "\n\nReply STOP to opt out.";
    }

    private static String firstName(Lead lead) {
        String n = lead.getName();
        if (n == null || n.isBlank()) return "there";
        String first = n.trim().split("\s+")[0];
        return first.isBlank() ? "there" : first;
    }
}
