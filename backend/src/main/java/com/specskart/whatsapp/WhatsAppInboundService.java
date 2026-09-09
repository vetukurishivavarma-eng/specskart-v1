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

    public WhatsAppInboundService(WebhookDedupe dedupe, LeadService leadService, WhatsAppBotService bot) {
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

        Map<String, Object> referral = in.referral() != null ? in.referral() : Map.of();
        Lead lead = leadService.onWhatsAppContact(in.waId(), in.phoneNumber(), in.profileName(), referral);
        bot.handleInbound(lead, in.text(), in.buttonId(), in.waMessageId());
    }
}
