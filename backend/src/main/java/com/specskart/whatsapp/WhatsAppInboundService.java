package com.specskart.whatsapp;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class WhatsAppInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);

    private final WebhookEventRepository webhookEvents;
    private final LeadService leadService;
    private final WhatsAppBotService bot;

    public WhatsAppInboundService(WebhookEventRepository webhookEvents, LeadService leadService, WhatsAppBotService bot) {
        this.webhookEvents = webhookEvents;
        this.leadService = leadService;
        this.bot = bot;
    }

    /** Idempotent: a repeated wa message id is acknowledged but not re-processed. */
    @Transactional
    public void process(InboundMessage in) {
        String dedupe = "wa-msg:" + (in.waMessageId() != null ? in.waMessageId() : in.waId() + ":" + System.nanoTime());
        if (webhookEvents.existsByDedupeKey(dedupe)) {
            log.info("duplicate webhook event {} ignored", dedupe);
            return;
        }
        WebhookEvent ev = new WebhookEvent();
        ev.setDedupeKey(dedupe);
        ev.setKind("inbound_message");
        webhookEvents.save(ev);

        Map<String, Object> referral = in.referral() != null ? in.referral() : Map.of();
        Lead lead = leadService.onWhatsAppContact(in.waId(), in.phoneNumber(), in.profileName(), referral);
        bot.handleInbound(lead, in.text(), in.buttonId(), in.waMessageId());
    }
}
