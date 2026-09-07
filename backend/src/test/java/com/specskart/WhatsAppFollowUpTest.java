package com.specskart;

import com.specskart.analytics.LeadEventRepository;
import com.specskart.analytics.LeadEventType;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppBotService;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "specskart.whatsapp.follow-up-template=reengage_v1")
@ActiveProfiles("mock")
class WhatsAppFollowUpTest {

    @Autowired WhatsAppInboundService inbound;
    @Autowired WhatsAppBotService bot;
    @Autowired LeadRepository leads;
    @Autowired LeadEventRepository events;
    @Autowired WhatsAppMessageRepository waMessages;
    @Autowired WhatsAppProvider provider;

    @Test
    void agentFollowUpGoesOutAsATemplateAndIsRecorded() {
        inbound.process(new InboundMessage("2609775555", "2609775555", "Cold Lead", "Hi", null, "fu-1", Map.of()));
        Lead lead = leads.findByWhatsappWaId("2609775555").orElseThrow();

        bot.sendManualFollowUp(lead);

        var sent = ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(s -> "reengage_v1".equals(s.templateName())).findFirst().orElseThrow();
        assertThat(sent.toWaId()).isEqualTo("2609775555");
        assertThat(sent.templateParams()).containsExactly("Cold", "Specskart");
        assertThat(waMessages.findByLeadIdOrderByCreatedAtAsc(lead.getId()))
                .anyMatch(m -> "template".equals(m.getMessageType()) && "OUTBOUND".equals(m.getDirection()));
        assertThat(events.findByLeadIdOrderByCreatedAtAsc(lead.getId()))
                .anyMatch(e -> e.getEventType() == LeadEventType.WHATSAPP_FOLLOW_UP_SENT);
    }
}
