package com.specskart;

import com.specskart.lead.EyeCheckupReminderService;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.AdminChatController;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** With approved templates configured: staff can open a chat with a brand-new number, and the
 *  eye-checkup reminder sends its template and stops once the customer replies. */
@SpringBootTest
@ActiveProfiles("mock")
@TestPropertySource(properties = {
        "specskart.whatsapp.follow-up-template=follow_test",
        "specskart.whatsapp.checkup-template=checkup_test",
        "specskart.whatsapp.checkup-link=https://maps.example/specskart"})
class CheckupAndNewChatTest {

    @Autowired AdminChatController chat;
    @Autowired EyeCheckupReminderService checkup;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppMessageRepository messages;
    @Autowired WhatsAppProvider provider;

    private static String randomLocal() {
        return "097" + (1000000 + (int) (Math.random() * 8999999));
    }

    private long templatesTo(String waId, String template) {
        return ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(s -> waId.equals(s.toWaId()) && template.equals(s.templateName())).count();
    }

    @Test
    void staffCanStartAChatWithANumberThatNeverMessagedUs() {
        String local = randomLocal();
        String waId = "260" + local.substring(1);   // local trunk 0 swapped for Zambia's code

        var out = chat.start(local, new AdminChatController.StartRequest("Mwila Banda"));

        assertThat(out.get("waId")).isEqualTo(waId);
        assertThat(leads.findByWhatsappWaId(waId)).get().extracting(Lead::getName).isEqualTo("Mwila Banda");
        assertThat(templatesTo(waId, "follow_test")).isEqualTo(1);
    }

    @Test
    void checkupReminderSendsTheTemplateThenStopsOnAReply() {
        String waId = "260" + randomLocal().substring(1);
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        l.setName("Chanda Phiri");
        l.setLastContactAt(Instant.now().minus(Duration.ofDays(3)));
        leads.save(l);

        checkup.start(l.getId());
        checkup.runDue(l.getId());
        assertThat(((MockWhatsAppProvider) provider).outbox()).anyMatch(s -> waId.equals(s.toWaId())
                && "checkup_test".equals(s.templateName())
                && s.templateParams().equals(java.util.List.of("Chanda", "https://maps.example/specskart")));
        Lead after = leads.findById(l.getId()).orElseThrow();
        assertThat(after.getCheckupStep()).isEqualTo(1);
        assertThat(after.getCheckupNextAt()).isAfter(Instant.now().plus(Duration.ofDays(1)));

        WhatsAppMessage reply = new WhatsAppMessage();       // customer answers
        reply.setLeadId(l.getId());
        reply.setDirection("INBOUND");
        reply.setMessageType("text");
        reply.setBody("ok I'll come Saturday");
        reply.setStatus("received");
        messages.save(reply);
        after.setCheckupNextAt(Instant.now().minusSeconds(1)); // next touch due now
        leads.save(after);

        checkup.runDue(l.getId());
        assertThat(templatesTo(waId, "checkup_test")).isEqualTo(1);
        assertThat(leads.findById(l.getId()).orElseThrow().getCheckupNextAt()).isNull();
    }
}
