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
        assertThatThrownBy(() -> broadcast.send(new BroadcastService.Filter(null, false, false), null, "en", null, null, 10))
                .hasMessageContaining("template");
    }

    @Test
    void skipsOptedOutLeadsAndSendsToTheRest() {
        String reachable = "26097" + (300000 + (int) (Math.random() * 600000));
        String optedOut = "26097" + (300000 + (int) (Math.random() * 600000));
        lead(reachable, FollowUpState.ACTIVE);
        lead(optedOut, FollowUpState.OPTED_OUT);

        int before = ((MockWhatsAppProvider) provider).outbox().size();
        var result = broadcast.send(new BroadcastService.Filter(null, false, false), "promo_v1", "en", null,
                java.util.List.of("Weekend sale"), 1000);

        assertThat(result.sent()).isGreaterThanOrEqualTo(1);
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        assertThat(outbox.size()).isGreaterThan(before);
        assertThat(outbox).noneMatch(s -> optedOut.equals(s.toWaId()));
        assertThat(outbox).anyMatch(s -> reachable.equals(s.toWaId()) && "promo_v1".equals(s.templateName()));
    }

    @Test
    void sendingTheSameTemplateAgainNeverReachesTheSamePersonTwice() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        lead(waId, FollowUpState.ACTIVE);
        var all = new BroadcastService.Filter(null, false, false);
        broadcast.send(all, "spin_and_win_t", "en", null, java.util.List.of(), 1000);
        assertThat(broadcast.previewCount(all, "spin_and_win_t")).isZero();

        int before = ((MockWhatsAppProvider) provider).outbox().size();
        assertThat(broadcast.send(all, "spin_and_win_t", "en", null, java.util.List.of(), 1000).sent()).isZero();
        assertThat(((MockWhatsAppProvider) provider).outbox()).hasSize(before);
        // a different template still reaches them
        assertThat(broadcast.previewCount(all, "other_t")).isGreaterThan(0);
    }

    @Autowired com.specskart.whatsapp.WhatsAppInboundService inbound;

    @Test
    void aTypedMessageGoesOnlyToOpenChatsAndOnlyOnce() {
        String open = "26097" + (300000 + (int) (Math.random() * 600000));
        String cold = "26097" + (300000 + (int) (Math.random() * 600000));
        inbound.process(new com.specskart.whatsapp.InboundMessage(open, open, "Open Chat", "hello", null, "bt:" + open, java.util.Map.of()));
        lead(cold, FollowUpState.ACTIVE); // never messaged: WhatsApp won't deliver free text to them

        var all = new BroadcastService.Filter(null, false, false);
        String text = "Shop closes early today " + open;
        assertThat(broadcast.previewText(all, text)).isGreaterThanOrEqualTo(1);
        broadcast.sendText(all, text, 500);
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        assertThat(outbox).anyMatch(s -> open.equals(s.toWaId()) && text.equals(s.text()));
        assertThat(outbox).noneMatch(s -> cold.equals(s.toWaId()) && text.equals(s.text()));

        int before = outbox.size();
        assertThat(broadcast.sendText(all, text, 500).sent()).isZero(); // nobody gets it twice
        assertThat(((MockWhatsAppProvider) provider).outbox()).hasSize(before);
        assertThatThrownBy(() -> broadcast.sendText(all, "  ", 10)).hasMessageContaining("Type the message");
    }

    @Test
    void aReadyMessageGoesFreeToOpenChatsAndPaidOnlyToTheRest() {
        String open = "26097" + (300000 + (int) (Math.random() * 600000));
        String cold = "26097" + (300000 + (int) (Math.random() * 600000));
        inbound.process(new com.specskart.whatsapp.InboundMessage(open, open, "Open Chat", "hello", null, "bf:" + open, java.util.Map.of()));
        lead(cold, FollowUpState.ACTIVE);

        String tpl = "ready_t_" + open;
        broadcast.send(new BroadcastService.Filter(null, false, false), tpl, "en", null, java.util.List.of(), 1000, "Reply Spin to play");
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        assertThat(outbox).anyMatch(s -> open.equals(s.toWaId()) && "Reply Spin to play".equals(s.text()));
        assertThat(outbox).noneMatch(s -> open.equals(s.toWaId()) && tpl.equals(s.templateName()));
        assertThat(outbox).anyMatch(s -> cold.equals(s.toWaId()) && tpl.equals(s.templateName()));
    }

    @Test
    void previewCountsWithoutSendingAnything() {
        int before = ((MockWhatsAppProvider) provider).outbox().size();
        int count = broadcast.previewCount(new BroadcastService.Filter(null, false, false), null);
        assertThat(count).isGreaterThanOrEqualTo(0);
        assertThat(((MockWhatsAppProvider) provider).outbox()).hasSize(before);
    }
}
