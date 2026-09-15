package com.specskart.lead;

import com.specskart.whatsapp.BroadcastService;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
@TestPropertySource(properties = "specskart.business-whatsapp-number=+260970000001")
class WalkInVerificationTest {

    @Autowired WalkInService walkIns;
    @Autowired WhatsAppInboundService inbound;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppProvider whatsapp;
    @Autowired BroadcastService broadcast;

    private static InboundMessage msg(String waId, String text) {
        return new InboundMessage(waId, waId, "mwila :)", text, null, "wamid.walkin." + System.nanoTime(), null);
    }

    @Test
    void sendingTheQrMessageVerifiesTheNumberOptsInAndStopWithdrawsIt() {
        var started = walkIns.start(null, "Mwila Phiri", null);
        assertThat(started.waLink()).startsWith("https://wa.me/260970000001?text=").contains(started.code());
        assertThat(walkIns.status(started.id()).verified()).isFalse();

        String waId = "26097" + (1_000_000 + (int) (Math.random() * 8_000_000));
        int optedInBefore = broadcast.previewCount(new BroadcastService.Filter(null, false, true));
        inbound.process(msg(waId, "Hi Specskart! Please save my number for offers & updates. Code: " + started.code()));

        var status = walkIns.status(started.id());
        assertThat(status.verified()).isTrue();
        assertThat(status.whatsappNumber()).isEqualTo("+" + waId);
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();
        assertThat(lead.getName()).isEqualTo("Mwila Phiri");
        assertThat(lead.getAcquisitionSource()).isEqualTo(AcquisitionSource.WALK_IN);
        assertThat(lead.getMarketingOptInAt()).isNotNull();
        assertThat(((MockWhatsAppProvider) whatsapp).outbox())
                .anyMatch(s -> waId.equals(s.toWaId()) && s.text() != null && s.text().contains("Reply STOP"));
        assertThat(broadcast.previewCount(new BroadcastService.Filter(null, false, true))).isEqualTo(optedInBefore + 1);

        // a used code can't be replayed from another phone
        String other = "26096" + (1_000_000 + (int) (Math.random() * 8_000_000));
        inbound.process(msg(other, "Code: " + started.code()));
        assertThat(leads.findByWhatsappWaId(other).orElseThrow().getMarketingOptInAt()).isNull();

        inbound.process(msg(waId, "STOP"));
        lead = leads.findByWhatsappWaId(waId).orElseThrow();
        assertThat(lead.getMarketingOptInAt()).isNull();
        assertThat(lead.getFollowUpState()).isEqualTo(FollowUpState.OPTED_OUT);
        assertThat(broadcast.previewCount(new BroadcastService.Filter(null, false, true))).isEqualTo(optedInBefore);
    }
}
