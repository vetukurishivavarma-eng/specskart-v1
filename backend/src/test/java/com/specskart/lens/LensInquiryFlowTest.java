package com.specskart.lens;

import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class LensInquiryFlowTest {

    @Autowired LensInquiryService service;
    @Autowired LensInquiryRepository inquiries;
    @Autowired WhatsAppProvider provider;

    private String phone() {
        return "097" + (7000000 + (int) (Math.random() * 900000));
    }

    /** The mock outbox is shared by every test in the suite, so assertions look for a
     *  message rather than assuming ours is the last one sent. */
    private java.util.List<String> sentTexts() {
        return ((MockWhatsAppProvider) provider).outbox().stream()
                .map(m -> m.text() == null ? "" : m.text()).toList();
    }

    /** The verify link is only ever sent over WhatsApp (by design — never handed back from
     *  start()), so tests recover the raw token the same way a real shopper would: from the
     *  message that landed in their chat. */
    private String tokenFromLastOutbound() {
        var sent = ((MockWhatsAppProvider) provider).outbox();
        String text = sent.get(sent.size() - 1).text();
        return text.substring(text.lastIndexOf('/') + 1);
    }

    @Test
    void detailsAreGatedUntilTheWhatsappNumberIsVerified() {
        UUID id = service.start(phone(), "CLEAR", false, null);
        assertThat(inquiries.findById(id).orElseThrow().isPhoneVerified()).isFalse();

        assertThatThrownBy(() -> service.update(id, new LensDtos.UpdateDetails(
                "Someone", 30, "M", null, null, null, null, null, null, null, null)))
                .hasMessageContaining("verify");

        boolean ok = service.verify(tokenFromLastOutbound());
        assertThat(ok).isTrue();
        assertThat(service.status(id).verified()).isTrue();

        var view = service.update(id, new LensDtos.UpdateDetails(
                "Someone", 30, "M", null, null, null, null, null, null, null, null));
        assertThat(view.customerName()).isEqualTo("Someone");
    }

    @Test
    void axisFarFromTheNormalBandsIsFlaggedSpecial() {
        UUID id = service.start(phone(), "PHOTOCHROMATIC", true, null);
        service.verify(tokenFromLastOutbound());

        // cyl present, axis 45 -> nowhere near 0/90/180 -> special
        var special = service.update(id, new LensDtos.UpdateDetails(
                null, null, null, null, null, BigDecimal.ONE, null, 45, null, null, null));
        assertThat(special.specialAxis()).isTrue();

        // axis 92 -> within 10 of 90 -> normal
        var normal = service.update(id, new LensDtos.UpdateDetails(
                null, null, null, null, null, BigDecimal.ONE, null, 92, null, null, null));
        assertThat(normal.specialAxis()).isFalse();
    }

    @Test
    void quotePicksTheClientPriceListRow() {
        UUID id = service.start(phone(), "PHOTOCHROMATIC", true, null);
        service.verify(tokenFromLastOutbound());
        service.update(id, new LensDtos.UpdateDetails(
                null, null, null, null, null, null, null, null, null,
                new BigDecimal("1.00"), "PROGRESSIVE"));
        var quoted = service.quote(id);
        assertThat(quoted.status()).isEqualTo("PRICED");
        assertThat(quoted.priceMinor()).isEqualTo(135_000L); // Colormatic Prog BB, Plano-2, Add 1-2

        // Colormatic BB single vision, SPH -5.00 -> ±4.25-±8.00 band, CYL -2.50 -> +K200
        service.update(id, new LensDtos.UpdateDetails(
                null, null, null, new BigDecimal("-5.00"), null, new BigDecimal("-2.50"), null, 90, null,
                BigDecimal.ZERO, null));
        assertThat(service.quote(id).priceMinor()).isEqualTo(99_900L + 20_000L);

        // bifocal with CYL -2.50 can't be a stock lens -> RX
        service.update(id, new LensDtos.UpdateDetails(
                null, null, null, null, null, null, null, null, null, new BigDecimal("2.00"), "BIFOCAL"));
        assertThat(service.quote(id).priceMinor()).isEqualTo(180_000L);

        // SPH beyond ±10 isn't on the list at all
        service.update(id, new LensDtos.UpdateDetails(
                null, null, null, new BigDecimal("-12.00"), null, BigDecimal.ZERO, null, null, null,
                BigDecimal.ZERO, null));
        assertThatThrownBy(() -> service.quote(id)).hasMessageContaining("outside our standard price list");
    }

    @Test
    void walkInSaleSkipsWhatsappAndIsBilledImmediately() {
        var sale = service.walkInSale(new LensDtos.WalkInSale(
                "Counter Customer", null, "CLEAR", true, null, null, null, null, null, null,
                "CASH", "Staff A", "Main Store", null, null, null, null, null, null, null, null));

        assertThat(sale.status()).isEqualTo("SOLD");
        assertThat(sale.priceMinor()).isEqualTo(36_000L); // Clear BB single vision

        var today = service.salesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(today).anySatisfy(s -> {
            assertThat(s.customerName()).isEqualTo("Counter Customer");
            assertThat(s.walkIn()).isTrue();
            assertThat(s.paymentMethod()).isEqualTo("CASH");
        });
        assertThat(service.summaryOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC)).totalMinor()).isGreaterThanOrEqualTo(33_000L);
    }

    @Test
    void walkInDiscountComesOffThePriceAndCantExceedIt() {
        var noDiscount = new LensDtos.WalkInSale("Discount Customer", null, "CLEAR", true, null, null, null, null, null, null,
                "CASH", "Staff A", "Main Store", null, null, null, null, null, null, null, null);
        assertThat(service.walkInQuote(noDiscount)).isEqualTo(36_000L);

        var sale = service.walkInSale(new LensDtos.WalkInSale("Discount Customer", null, "CLEAR", true, null, null, null, null, null, null,
                "CASH", "Staff A", "Main Store", null, null, 5_000L, null, null, null, null, null));
        assertThat(sale.priceMinor()).isEqualTo(31_000L);

        assertThatThrownBy(() -> service.walkInSale(new LensDtos.WalkInSale("Too Much", null, "CLEAR", true, null, null, null, null, null, null,
                "CASH", "Staff A", "Main Store", null, null, 36_001L, null, null, null, null, null))).hasMessageContaining("Discount");
    }

    @Test
    void counterOrderWithDepositGoesToTheLabQueueAndBalanceIsTakenAtPickup() {
        String phone = "097" + (1_000_000 + (int) (Math.random() * 8_999_999));
        var sale = service.walkInSale(new LensDtos.WalkInSale("Deposit Customer", phone, "CLEAR", true,
                new BigDecimal("-2.00"), new BigDecimal("-1.75"), new BigDecimal("-0.75"), null, null, null,
                "CASH", "Staff A", "Main Store", "dep-" + UUID.randomUUID(), null, null,
                90, 85, "62", 10_000L, null));

        assertThat(sale.status()).isEqualTo("SOLD");
        assertThat(sale.fulfilment()).isEqualTo("ORDERED");
        assertThat(sale.axisRight()).isEqualTo(90);
        assertThat(sale.pd()).isEqualTo("62");
        assertThat(sale.balanceMinor()).isEqualTo(26_000L); // 36,000 - 10,000 deposit
        assertThat(service.pendingWebOrders(false)).anySatisfy(s -> {
            assertThat(s.id()).isEqualTo(sale.id());
            assertThat(s.balanceMinor()).isEqualTo(26_000L);
        });
        var toCustomer = ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(m -> m.toWaId() != null && m.toWaId().endsWith(phone.substring(1)))
                .map(m -> m.text() == null ? "" : m.text()).toList();
        assertThat(toCustomer).anyMatch(t -> t.contains("we'll message you here when it's ready") && t.contains("K260"));

        service.advanceFulfilment(sale.id(), new LensDtos.AdvanceFulfilment("READY", null, null, null));
        assertThatThrownBy(() -> service.advanceFulfilment(sale.id(), new LensDtos.AdvanceFulfilment("DELIVERED", null, "Staff B", null)))
                .hasMessageContaining("Pick how they paid");
        var collected = service.advanceFulfilment(sale.id(), new LensDtos.AdvanceFulfilment("DELIVERED", "MOBILE", "Staff B", null));
        assertThat(collected.fulfilment()).isEqualTo("DELIVERED");
        assertThat(collected.balanceMinor()).isZero();
    }

    @Test
    void lensesTakenAwayNowMustBePaidInFull() {
        assertThatThrownBy(() -> service.walkInSale(new LensDtos.WalkInSale("Now Customer", null, "CLEAR", true,
                null, null, null, null, null, null, "CASH", "Staff A", null, null, null, null,
                null, null, null, 5_000L, true))).hasMessageContaining("paid in full");

        var now = service.walkInSale(new LensDtos.WalkInSale("Now Customer", null, "CLEAR", true,
                null, null, null, null, null, null, "CASH", "Staff A", null, null, null, null,
                null, null, null, null, true));
        assertThat(now.fulfilment()).isEqualTo("DELIVERED");
        assertThat(service.pendingWebOrders(false)).noneMatch(s -> s.id().equals(now.id()));
    }

    @Test
    void completingAWebOrderMovesItFromPendingToSold() {
        UUID id = service.start(phone(), "CLEAR", false, null);
        service.verify(tokenFromLastOutbound());
        service.submit(id);

        assertThat(service.pendingWebOrders()).anySatisfy(s -> assertThat(s.id()).isEqualTo(id));

        var sold = service.completeSale(id, new LensDtos.CompleteSale("MOBILE", "Staff B", "Main Store"));
        assertThat(sold.status()).isEqualTo("SOLD");
        assertThat(service.pendingWebOrders()).noneSatisfy(s -> assertThat(s.id()).isEqualTo(id));

        // the customer hears about it -- they'd had nothing since the lens link
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("have been collected"));
    }

    @Test
    void aWebOrderNeedsNothingButAVerifiedNumberAndIsCollectedAtTheShop() {
        UUID id = service.start(phone(), "CLEAR", false, null);
        service.verify(tokenFromLastOutbound());

        assertThat(service.submit(id).status()).isEqualTo("SUBMITTED");

        // placing the order confirms it to the customer, not just the lab
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("got your lens order"));

        // and the lab's paperwork says nobody is delivering it
        assertThat(LensInquiryService.staffLines(inquiries.findById(id).orElseThrow()))
                .contains("Collection: customer picks up at the shop");
    }

    @Test
    void aWebOrderWalksTheLadderAndIsBilledWhenItIsCollected() {
        UUID id = service.start(phone(), "CLEAR", false, null);
        service.verify(tokenFromLastOutbound());
        service.submit(id);
        assertThat(inquiries.findById(id).orElseThrow().getFulfilment()).isEqualTo("ORDERED");

        // forward one rung at a time: no skipping straight to collected
        assertThatThrownBy(() -> service.advanceFulfilment(id,
                new LensDtos.AdvanceFulfilment("DELIVERED", "CASH", "Staff B", "Main Store")))
                .hasMessageContaining("one step at a time");

        var out = service.advanceFulfilment(id, new LensDtos.AdvanceFulfilment("READY", null, null, null));
        assertThat(out.status()).isEqualTo("SUBMITTED"); // still unpaid — they pay at the counter
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("Collect them at:"));
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("send someone to pick them up"));

        // an in-flight order stays on the staff list the whole way
        assertThat(service.pendingWebOrders()).anySatisfy(v -> assertThat(v.id()).isEqualTo(id));

        var done = service.advanceFulfilment(id,
                new LensDtos.AdvanceFulfilment("DELIVERED", "CASH", "Staff B", "Main Store"));
        assertThat(done.status()).isEqualTo("SOLD"); // handing it over is what bills it
        assertThat(service.pendingWebOrders()).noneSatisfy(v -> assertThat(v.id()).isEqualTo(id));
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("have been collected"));
    }

    @Test
    void payingOnlineMarksTheOrderPaidAndSurvivesTheHandover() {
        UUID id = service.start(phone(), "CLEAR", false, null);
        service.verify(tokenFromLastOutbound());
        service.submit(id);

        var pay = service.startPayment(id);
        assertThat(pay.checkoutUrl()).isNotBlank();
        assertThat(pay.amountMinor()).isEqualTo(32_000L);

        String txRef = "LENS-" + id;
        assertThat(LensInquiryService.isLensRef(txRef)).isTrue();
        assertThat(LensInquiryService.isLensRef("SK-ABC123")).isFalse();

        service.confirmPayment(txRef);
        assertThat(service.status(id).paid()).isTrue();
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("received your payment"));

        // paying twice is a no-op, and a second charge can't be started
        service.confirmPayment(txRef);
        assertThatThrownBy(() -> service.startPayment(id)).hasMessageContaining("already paid");

        // the handover must not overwrite ONLINE with whatever staff tapped
        service.advanceFulfilment(id, new LensDtos.AdvanceFulfilment("READY", null, null, null));
        service.advanceFulfilment(id, new LensDtos.AdvanceFulfilment("DELIVERED", "CASH", "Staff B", "Main Store"));
        assertThat(service.salesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC)))
                .filteredOn(v -> v.id().equals(id))
                .allSatisfy(v -> assertThat(v.paymentMethod()).isEqualTo("ONLINE"));
    }

    @Test
    void replayingAWalkInSaleWithTheSameClientReferenceDoesNotDoubleSell() {
        String ref = "device-" + UUID.randomUUID();
        var req = new LensDtos.WalkInSale("Offline Customer", null, "CLEAR", false, null, null, null, null, null, null,
                "CASH", "Staff A", "Main Store", ref, null, null, null, null, null, null, null);

        var first = service.walkInSale(req);
        var replay = service.walkInSale(req);

        assertThat(replay.id()).isEqualTo(first.id());
        long matching = service.salesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC)).stream()
                .filter(s -> "Offline Customer".equals(s.customerName())).count();
        assertThat(matching).isEqualTo(1);
    }

    /**
     * A number that has verified once is not asked again. The second inquiry comes back already
     * verified, so the configurator opens straight onto the form instead of sending the customer
     * off to WhatsApp to find a link.
     */
    @Test
    void aKnownNumberSkipsVerificationTheSecondTime() {
        String phone = phone();

        UUID first = service.start(phone, "CLEAR", false, null);
        assertThat(service.status(first).verified()).isFalse();

        // Prove the number once, the way the verify link does.
        LensInquiry q = inquiries.findById(first).orElseThrow();
        q.setPhoneVerifiedAt(java.time.Instant.now());
        q.setStatus("VERIFIED");
        inquiries.save(q);

        UUID second = service.start(phone, "PHOTOCHROMATIC", true, null);
        assertThat(service.status(second).verified()).isTrue();
        // Same person, a genuinely new order -- not the old row handed back.
        assertThat(second).isNotEqualTo(first);
    }
}
