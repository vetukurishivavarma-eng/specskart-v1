package com.specskart.whatsapp;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class ServiceWindowTest {

    @Autowired ServiceWindow window;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppMessageRepository messages;

    @Test
    void openOnlyAfterTheNumberMessagedUs() {
        String waId = "26097" + (1_000_000 + (int) (Math.random() * 8_999_999));
        Lead lead = new Lead();
        lead.setWhatsappWaId(waId);
        leads.save(lead);
        assertThat(window.isOpen("+" + waId)).isFalse();      // never wrote to us
        assertThat(window.isOpen("26000000000")).isFalse();    // unknown number

        WhatsAppMessage in = new WhatsAppMessage();
        in.setLeadId(lead.getId());
        in.setDirection("INBOUND");
        in.setMessageType("text");
        in.setBody("hi");
        messages.save(in);
        assertThat(window.isOpen("+" + waId)).isTrue();       // staff numbers carry a +
    }
}
