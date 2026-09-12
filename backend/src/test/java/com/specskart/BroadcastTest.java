package com.specskart;

import com.specskart.lead.FollowUpState;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.BroadcastService;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class BroadcastTest {

    @Autowired BroadcastService broadcast;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppProvider provider;

    private Lead lead(String waId, FollowUpState state) {
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        l.setFollowUpState(state);
        return leads.save(l);
    }

    @Test
    void requiresATemplate() {
        assertThatThrownBy(() -> broadcast.send(new BroadcastService.Filter(null, false), null, "en", null, null, 10))
                .hasMessageContaining("template");
    }

    @Test
    void skipsOptedOutLeadsAndSendsToTheRest() {
        String reachable = "26097" + (300000 + (int) (Math.random() * 600000));
        String optedOut = "26097" + (300000 + (int) (Math.random() * 600000));
        lead(reachable, FollowUpState.ACTIVE);
        lead(optedOut, FollowUpState.OPTED_OUT);

        int before = ((MockWhatsAppProvider) provider).outbox().size();
        var result = broadcast.send(new BroadcastService.Filter(null, false), "promo_v1", "en", null,
                java.util.List.of("Weekend sale"), 1000);

        assertThat(result.sent()).isGreaterThanOrEqualTo(1);
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        assertThat(outbox.size()).isGreaterThan(before);
        assertThat(outbox).noneMatch(s -> optedOut.equals(s.toWaId()));
        assertThat(outbox).anyMatch(s -> reachable.equals(s.toWaId()) && "promo_v1".equals(s.templateName()));
    }

    @Test
    void previewCountsWithoutSendingAnything() {
        int before = ((MockWhatsAppProvider) provider).outbox().size();
        int count = broadcast.previewCount(new BroadcastService.Filter(null, false));
        assertThat(count).isGreaterThanOrEqualTo(0);
        assertThat(((MockWhatsAppProvider) provider).outbox()).hasSize(before);
    }
}
