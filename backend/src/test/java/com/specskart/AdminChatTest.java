package com.specskart;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.AdminChatController;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppBotService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class AdminChatTest {

    @Autowired AdminChatController chat;
    @Autowired WhatsAppBotService bot;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppProvider provider;

    private String newLead() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        leads.save(l);
        return waId;
    }

    private long sentTo(String waId) {
        return ((MockWhatsAppProvider) provider).outbox().stream().filter(s -> waId.equals(s.toWaId())).count();
    }

    private void customerSays(String waId, String text) {
        bot.handleInbound(leads.findByWhatsappWaId(waId).orElseThrow(), text, null, "wamid." + UUID.randomUUID());
    }

    @Test
    void staffMessageGoesFromBusinessNumberAndKeepsTheBotQuietUntilHandedBack() {
        String waId = newLead();
        customerSays(waId, "hello");                 // opens the 24h window; bot answers
        long afterGreeting = sentTo(waId);
        assertThat(afterGreeting).isGreaterThan(0);

        chat.send(waId, new AdminChatController.SendRequest("Hi, your glasses are ready"));
        assertThat(((MockWhatsAppProvider) provider).outbox())
                .anyMatch(s -> waId.equals(s.toWaId()) && "Hi, your glasses are ready".equals(s.text()));
        long afterStaff = sentTo(waId);

        customerSays(waId, "ok thanks");             // off-script: the bot would send its menu
        assertThat(sentTo(waId)).isEqualTo(afterStaff);
        assertThat(chat.get(waId).get("botQuietUntil")).isNotNull();

        chat.resumeBot(waId);
        customerSays(waId, "menu");
        assertThat(sentTo(waId)).isGreaterThan(afterStaff);
    }

    @Test
    void refusesPlainTextOutsideTheServiceWindow() {
        String waId = newLead();                     // never messaged us: window shut
        assertThatThrownBy(() -> chat.send(waId, new AdminChatController.SendRequest("Hello")))
                .hasMessageContaining("24 hours");
        assertThat(sentTo(waId)).isZero();
    }
}
