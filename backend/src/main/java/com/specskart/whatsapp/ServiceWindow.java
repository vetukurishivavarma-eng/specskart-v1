package com.specskart.whatsapp;

import com.specskart.lead.LeadRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Is Meta's 24-hour customer-service window open for this number -- did they message us in the
 *  last 24h? Inside it a plain message is free and needs no approved template, billing or
 *  template review, so notifications prefer it there and keep templates for when it's shut. */
@Component
public class ServiceWindow {

    private final LeadRepository leads;
    private final WhatsAppMessageRepository messages;

    public ServiceWindow(LeadRepository leads, WhatsAppMessageRepository messages) {
        this.leads = leads;
        this.messages = messages;
    }

    public boolean isOpen(String waId) {
        if (waId == null) return false;
        String digits = waId.replaceAll("[^0-9]", "");
        return leads.findByWhatsappWaId(digits)
                .flatMap(l -> messages.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(l.getId(), "INBOUND"))
                .map(m -> m.getCreatedAt().isAfter(Instant.now().minus(24, ChronoUnit.HOURS)))
                .orElse(false);
    }
}
