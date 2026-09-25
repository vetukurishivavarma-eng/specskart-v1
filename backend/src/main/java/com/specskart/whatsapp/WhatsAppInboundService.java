package com.specskart.whatsapp;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class WhatsAppInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);

    private final WebhookDedupe dedupe;
    private final LeadService leadService;
    private final WhatsAppBotService bot;
    private final com.specskart.lead.WalkInService walkIns;

    public WhatsAppInboundService(WebhookDedupe dedupe, LeadService leadService, WhatsAppBotService bot,
                                  com.specskart.lead.WalkInService walkIns) {
        this.walkIns = walkIns;
        this.dedupe = dedupe;
        this.leadService = leadService;
        this.bot = bot;
    }

    /** Idempotent: a repeated wa message id is acknowledged but not re-processed. */
    @Transactional
    public void process(InboundMessage in) {
        if (in.waMessageId() == null) {
            log.warn("inbound WhatsApp message with no id — cannot dedupe, processing once");
        }
        String dedupeKey = "wa-msg:" + (in.waMessageId() != null
                ? in.waMessageId() : in.waId() + ":" + System.nanoTime());
        boolean claimed;
        try {
            claimed = dedupe.claim(dedupeKey, "inbound_message");
        } catch (DataAccessException raceLost) {
            claimed = false; // a concurrent delivery of the same event won the insert
        }
        if (!claimed) {
            log.info("duplicate webhook event {} ignored", dedupeKey);
            return;
        }

        Map<String, Object> referral = in.referral() != null && !in.referral().isEmpty()
                ? in.referral() : Map.of("utm_source", sourceFromText(in.text()));
        Lead lead = leadService.onWhatsAppContact(in.waId(), in.phoneNumber(), in.profileName(), referral);
        if (walkIns.tryVerify(lead, in.text())) return; // shop walk-in QR sign-up, not a bot conversation
        bot.handleInbound(lead, in.text(), in.buttonId(), in.waMessageId());
    }

    /**
     * Only a Meta click-to-WhatsApp ad hands us a referral. A TikTok or Google "chat now" button
     * is a plain wa.me link, so its ad is identified by the prefilled first message instead
     * (e.g. {@code wa.me/<number>?text=Hi Specskart, I saw your ad on TikTok}). Anything else
     * with no referral is someone messaging us directly -- WHATSAPP, not the WEBSITE fallback.
     * Only used on first contact; a returning lead keeps its original source.
     */
    static String sourceFromText(String text) {
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains("tiktok")) return "tiktok";
        if (t.contains("google")) return "google";
        return "whatsapp";
    }
}
