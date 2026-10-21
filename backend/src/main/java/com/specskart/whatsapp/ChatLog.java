package com.specskart.whatsapp;

import com.specskart.lead.LeadRepository;
import org.springframework.stereotype.Component;

/**
 * Puts a message the system sent (back in stock, order and lens updates, recalls...) into the
 * customer's POS chat, the way the bot's own replies are. Those senders used to send and not
 * record, so staff saw a customer answer a message that wasn't in the chat.
 */
@Component
public class ChatLog {

    private final LeadRepository leads;
    private final WhatsAppMessageRepository messages;

    public ChatLog(LeadRepository leads, WhatsAppMessageRepository messages) {
        this.leads = leads;
        this.messages = messages;
    }

    /** No-op for a number that isn't a lead (e.g. a staff phone). */
    public void sent(String waId, String body) {
        String digits = waId == null ? "" : waId.replaceAll("[^0-9]", "");
        leads.findByWhatsappWaId(digits).ifPresent(lead -> {
            WhatsAppMessage m = new WhatsAppMessage();
            m.setLeadId(lead.getId());
            m.setDirection("OUTBOUND");
            m.setMessageType("text");
            m.setBody(body);
            m.setStatus("sent");
            messages.save(m);
        });
    }
}
