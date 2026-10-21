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
        Lead l = customer();
        String waId = l.getWhatsappWaId();
        String code = won(l, "Free sunglasses with your first pair", "H").getCode(); // not a friend prize

        assertThat(spins.lookup(code.substring(5).toLowerCase()).state()).isEqualTo("VALID"); // typed without SPIN-
        // the number must be the customer's own WhatsApp, and a verification code must have been sent
        assertThatThrownBy(() -> spins.sendRedeemCode(code, "260971111111")).hasMessageContaining("different WhatsApp number");
        assertThatThrownBy(() -> spins.redeem(code, waId, "123456", "Staff")).hasMessageContaining("Send code");

        assertThat(spins.sendRedeemCode(code, "+" + waId).sentTo()).endsWith(waId.substring(waId.length() - 4));
        String otp = lastOtp(waId);
        String wrong = otp.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> spins.redeem(code, waId, wrong, "Staff")).hasMessageContaining("wrong");
        assertThat(spinRows.findByCodeIgnoreCase(code).orElseThrow().getOtpTries()).isEqualTo(1); // counted despite the throw
        assertThat(spins.redeem(code, waId, otp, "Staff").state()).isEqualTo("USED");
        assertThatThrownBy(() -> spins.sendRedeemCode(code, waId)).hasMessageContaining("already used");
    }

    @Test
    void fiveWrongVerificationCodesNeedANewOne() {
        Lead l = customer();
        String code = won(l, "Free sunglasses with your first pair", "M").getCode();
        spins.sendRedeemCode(code, l.getWhatsappWaId());
        String otp = lastOtp(l.getWhatsappWaId());
        String wrong = otp.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < SpinService.OTP_TRIES; i++) {
            assertThatThrownBy(() -> spins.redeem(code, l.getWhatsappWaId(), wrong, "Staff")).hasMessageContaining("wrong");
        }
        assertThatThrownBy(() -> spins.redeem(code, l.getWhatsappWaId(), otp, "Staff")).hasMessageContaining("Too many");
    }

    @Test
    void codesCanBeSentOnceAMinuteAndFiveTimesAtMost() {
        Lead l = customer();
        String waId = l.getWhatsappWaId();
        String code = won(l, "Free sunglasses with your first pair", "R").getCode();
        var first = spins.sendRedeemCode(code, waId);
        assertThat(first.resendInSeconds()).isEqualTo(60);
        assertThat(first.sendsLeft()).isEqualTo(4);
        assertThatThrownBy(() -> spins.sendRedeemCode(code, waId)).hasMessageContaining("Wait");

        Spin s = spinRows.findByCodeIgnoreCase(code).orElseThrow();
        s.setOtpSentAt(java.time.Instant.now().minusSeconds(61)); // a minute later
        spinRows.saveAndFlush(s);
        assertThat(spins.sendRedeemCode(code, waId).sendsLeft()).isEqualTo(3);

        s = spinRows.findByCodeIgnoreCase(code).orElseThrow();
        s.setOtpSentAt(java.time.Instant.now().minusSeconds(61));
        s.setOtpSends(SpinService.MAX_OTP_SENDS);
        spinRows.saveAndFlush(s);
        assertThatThrownBy(() -> spins.sendRedeemCode(code, waId)).hasMessageContaining("Too many verification codes");
    }

    /** The verification code in the last WhatsApp sent to this number. */
    private String lastOtp(String waId) {
        var m = ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(s -> waId.equals(s.toWaId()) && s.text() != null && s.text().contains("verification code is"))
                .reduce((a, b) -> b).orElseThrow().text();
        var match = java.util.regex.Pattern.compile("\\*(\\d{6})\\*").matcher(m);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }

    private SpinService.SpinView redeemWithOtp(String code, String waId) {
        spins.sendRedeemCode(code, waId);
        return spins.redeem(code, waId, lastOtp(waId), "Staff");
    }

    @Test
    void aBringAFriendCodeUnlocksOnlyWhenANewFriendMessagesWithIt() {
        Lead winner = customer();
        Spin s = won(winner, FRIEND, "F");

        assertThat(spins.lookup(s.getCode()).state()).isEqualTo("WAITING_FRIEND");
        assertThatThrownBy(() -> spins.sendRedeemCode(s.getCode(), winner.getWhatsappWaId())).hasMessageContaining("Not unlocked");
        assertThat(spins.claimFriend(winner, s.getCode())).contains("your own code");

        // the friend messages us with the code (the forwarded link's prefilled text)
        String waId = "26097" + (100000 + (int) (Math.random() * 800000));
        inbound.process(new InboundMessage(waId, waId, "Mutale Banda", "Hi! My friend's code is " + s.getCode(), null, "f:" + waId, Map.of()));
        Spin after = spinRows.findById(s.getId()).orElseThrow();
        assertThat(after.getFriendCode()).startsWith("SPIN-").isNotEqualTo(s.getCode());
        assertThat(((MockWhatsAppProvider) provider).outbox().stream()
                .filter(m -> waId.equals(m.toWaId()) && m.text() != null))
                .anyMatch(m -> m.text().contains(after.getFriendCode()));

        // each code only with its own holder's number
        assertThatThrownBy(() -> spins.sendRedeemCode(after.getFriendCode(), winner.getWhatsappWaId())).hasMessageContaining("different WhatsApp number");
        assertThat(redeemWithOtp(s.getCode(), winner.getWhatsappWaId()).state()).isEqualTo("USED");
        assertThat(redeemWithOtp(after.getFriendCode(), waId).state()).isEqualTo("USED");
        assertThatThrownBy(() -> spins.sendRedeemCode(after.getFriendCode(), waId)).hasMessageContaining("already used");

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
