package com.specskart.spin;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.shared.ApiException;
import com.specskart.shared.SignedLinks;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spin & Win: every spin wins, one spin per customer, a code is used once. */
@SpringBootTest
@ActiveProfiles("mock")
class SpinTest {

    @Autowired SpinService spins;
    @Autowired SpinRepository spinRows;
    @Autowired LeadRepository leads;
    @Autowired SignedLinks links;
    @Autowired WhatsAppInboundService inbound;
    @Autowired WhatsAppProvider provider;

    private Lead customer() {
        String waId = "26096" + (100000 + (int) (Math.random() * 800000));
        inbound.process(new InboundMessage(waId, waId, "Chanda Mwila", "spin", null, "spin:" + waId, Map.of()));
        return leads.findByWhatsappWaId(waId).orElseThrow();
    }

    /** The exp + sig the bot's link carries. */
    private long[] expSig(Lead l, String[] sig) {
        String q = links.query("spin/" + l.getId(), Duration.ofDays(1)); // ?exp=..&sig=..
        long exp = Long.parseLong(q.substring(5, q.indexOf('&')));
        sig[0] = q.substring(q.indexOf("sig=") + 4);
        return new long[]{exp};
    }

    @Test
    void typingSpinGetsTheLinkAndOneSpinOnly() {
        Lead l = customer();
        var sent = ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(s -> l.getWhatsappWaId().equals(s.toWaId()) && s.text() != null).toList();
        assertThat(sent).anyMatch(s -> s.text().contains("Spin & Win") && s.text().contains("/spin/" + l.getId()));

        String[] sig = new String[1];
        long exp = expSig(l, sig)[0];
        var first = spins.spin(l.getId(), exp, sig[0]);
        assertThat(first.code()).startsWith("SPIN-");
        assertThat(SpinService.DEFAULT_PRIZES.stream().map(d -> d[0])).contains(first.prize());

        // a second tap / reload / other phone: same prize, no new spin
        var again = spins.spin(l.getId(), exp, sig[0]);
        assertThat(again.code()).isEqualTo(first.code());
        assertThat(spinRows.findAll().stream().filter(s -> s.getLeadId().equals(l.getId()))).hasSize(1);
        assertThat(spins.offer(l)).contains("already had your spin").contains(first.code());
    }

    @Test
    void aForgedOrExpiredLinkCannotSpin() {
        Lead l = customer();
        assertThatThrownBy(() -> spins.spin(l.getId(), System.currentTimeMillis() / 1000 + 999, "forged"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> spins.spin(UUID.randomUUID(), 1, "x")).isInstanceOf(ApiException.class);
    }

    @Test
    void aCodeIsUsedOnceAtTheShop() {
        String code = won(customer(), "Free sunglasses with your first pair", "H").getCode(); // not a friend prize

        assertThat(spins.lookup(code.substring(5).toLowerCase()).state()).isEqualTo("VALID"); // typed without SPIN-
        assertThat(spins.redeem(code, "Staff").state()).isEqualTo("USED");
        assertThatThrownBy(() -> spins.redeem(code, "Staff")).hasMessageContaining("already used");
    }

    @Test
    void aBringAFriendCodeUnlocksOnlyWhenANewFriendMessagesWithIt() {
        Lead winner = customer();
        Spin s = won(winner, FRIEND, "F");

        assertThat(spins.lookup(s.getCode()).state()).isEqualTo("WAITING_FRIEND");
        assertThatThrownBy(() -> spins.redeem(s.getCode(), "Staff")).hasMessageContaining("Not unlocked");
        assertThat(spins.claimFriend(winner, s.getCode())).contains("your own code");

        // the friend messages us with the code (the forwarded link's prefilled text)
        String waId = "26097" + (100000 + (int) (Math.random() * 800000));
        inbound.process(new InboundMessage(waId, waId, "Mutale Banda", "Hi! My friend's code is " + s.getCode(), null, "f:" + waId, Map.of()));
        Spin after = spinRows.findById(s.getId()).orElseThrow();
        assertThat(after.getFriendCode()).startsWith("SPIN-").isNotEqualTo(s.getCode());
        assertThat(((MockWhatsAppProvider) provider).outbox().stream()
                .filter(m -> waId.equals(m.toWaId()) && m.text() != null))
                .anyMatch(m -> m.text().contains(after.getFriendCode()));

        assertThat(spins.redeem(s.getCode(), "Staff").state()).isEqualTo("USED");
        assertThat(spins.redeem(after.getFriendCode(), "Staff").state()).isEqualTo("USED");
        assertThatThrownBy(() -> spins.redeem(after.getFriendCode(), "Staff")).hasMessageContaining("already used");

        // a second friend can't use it
        Lead late = customer();
        assertThat(spins.claimFriend(late, s.getCode())).contains("already used this code");

        // the friend can't spin while their friend code is in its week; once it's over, both can
        Lead friend = leads.findByWhatsappWaId(waId).orElseThrow();
        String[] sig = new String[1];
        long exp = expSig(friend, sig)[0];
        assertThatThrownBy(() -> spins.spin(friend.getId(), exp, sig[0])).hasMessageContaining("You can spin after it expires");
        assertThat(spins.offer(friend)).contains(after.getFriendCode());

        after.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        spinRows.saveAndFlush(after);
        assertThat(spins.spin(friend.getId(), exp, sig[0]).code()).isNotIn(s.getCode(), after.getFriendCode());
        long wExp = expSig(winner, sig)[0];
        assertThat(spins.spin(winner.getId(), wExp, sig[0]).code()).isNotEqualTo(s.getCode());
    }

    @Test
    void anExistingCustomerCannotBeTheFriend() {
        Lead old = customer();
        Spin s = won(customer(), FRIEND, "G");
        assertThat(spins.claimFriend(old, s.getCode())).contains("new customers only");
        assertThat(spinRows.findById(s.getId()).orElseThrow().getFriendLeadId()).isNull();
    }

    @Test
    void someoneHoldingAPrizeCodeCannotTakeAFriendsToo() {
        Spin s = won(customer(), FRIEND, "J");
        Lead newcomer = customer(); // new since the spin, but already won their own prize
        won(newcomer, "Free sunglasses with your first pair", "K");
        assertThat(spins.claimFriend(newcomer, s.getCode())).contains("You already have a prize code");
        assertThat(spinRows.findById(s.getId()).orElseThrow().getFriendLeadId()).isNull();
    }

    private static final String FRIEND = "Bring a friend: K100 off for you both";

    /** A spin row with a chosen prize (the wheel itself is random). */
    private Spin won(Lead lead, String prize, String tag) {
        Spin s = new Spin();
        s.setLeadId(lead.getId());
        s.setPrizeLabel(prize);
        s.setCode("SPIN-" + tag + (1000 + (int) (Math.random() * 8999)));
        s.setExpiresAt(java.time.Instant.now().plus(Duration.ofDays(7)));
        return spinRows.saveAndFlush(s);
    }

    @Test
    void theWheelPicksByChance() {
        SpinPrize a = prize("A", 50), b = prize("B", 35), c = prize("C", 15);
        List<SpinPrize> wheel = List.of(a, b, c);
        assertThat(SpinService.pick(wheel, 0)).isSameAs(a);
        assertThat(SpinService.pick(wheel, 49)).isSameAs(a);
        assertThat(SpinService.pick(wheel, 50)).isSameAs(b);
        assertThat(SpinService.pick(wheel, 84)).isSameAs(b);
        assertThat(SpinService.pick(wheel, 85)).isSameAs(c);
        assertThat(SpinService.pick(wheel, 99)).isSameAs(c);
    }

    private static SpinPrize prize(String label, int weight) {
        SpinPrize p = new SpinPrize();
        p.setLabel(label);
        p.setWeight(weight);
        return p;
    }
}
