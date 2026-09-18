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
        UUID id = service.start(phone(), "CLEAR", false);
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
        UUID id = service.start(phone(), "PHOTOCHROMATIC", true);
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
    void quoteAddsBlueBlockAndProgressiveOnTopOfTheBaseLens() {
        UUID id = service.start(phone(), "PHOTOCHROMATIC", true);
        service.verify(tokenFromLastOutbound());
        service.update(id, new LensDtos.UpdateDetails(
                null, null, null, null, null, null, null, null, null,
                new BigDecimal("1.00"), "PROGRESSIVE"));

        var quoted = service.quote(id);
        assertThat(quoted.status()).isEqualTo("PRICED");
        assertThat(quoted.priceMinor()).isEqualTo(45_000L + 8_000L + 35_000L);
    }

    @Test
    void walkInSaleSkipsWhatsappAndIsBilledImmediately() {
        var sale = service.walkInSale(new LensDtos.WalkInSale(
                "Counter Customer", null, "CLEAR", true, null, null,
                "CASH", "Staff A", "Main Store", null));

        assertThat(sale.status()).isEqualTo("SOLD");
        assertThat(sale.priceMinor()).isEqualTo(25_000L + 8_000L);

        var today = service.salesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(today).anySatisfy(s -> {
            assertThat(s.customerName()).isEqualTo("Counter Customer");
            assertThat(s.walkIn()).isTrue();
            assertThat(s.paymentMethod()).isEqualTo("CASH");
        });
        assertThat(service.summaryOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC)).totalMinor()).isGreaterThanOrEqualTo(33_000L);
    }

    @Test
    void completingAWebOrderMovesItFromPendingToSold() {
        UUID id = service.start(phone(), "CLEAR", false);
        service.verify(tokenFromLastOutbound());
        service.setDelivery(id, new LensDtos.Delivery("Ann", "12 Freedom Way", "Kabulonga", null));
        service.submit(id);

        assertThat(service.pendingWebOrders()).anySatisfy(s -> assertThat(s.id()).isEqualTo(id));

        var sold = service.completeSale(id, new LensDtos.CompleteSale("MOBILE", "Staff B", "Main Store"));
        assertThat(sold.status()).isEqualTo("SOLD");
        assertThat(service.pendingWebOrders()).noneSatisfy(s -> assertThat(s.id()).isEqualTo(id));

        // the customer hears about it -- they'd had nothing since the lens link
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("have been delivered"));
    }

    @Test
    void aWebOrderCannotBeSubmittedWithNowhereToDeliverIt() {
        UUID id = service.start(phone(), "CLEAR", false);
        service.verify(tokenFromLastOutbound());

        assertThatThrownBy(() -> service.submit(id)).hasMessageContaining("delivery address");

        // area is as required as the street -- an address alone doesn't route a courier
        assertThatThrownBy(() -> service.setDelivery(id,
                new LensDtos.Delivery("Ann", "12 Freedom Way", "  ", null)))
                .hasMessageContaining("area");

        service.setDelivery(id, new LensDtos.Delivery("Ann", "12 Freedom Way", "Kabulonga", "opp. the bank"));
        assertThat(service.submit(id).status()).isEqualTo("SUBMITTED");

        // placing the order confirms it to the customer, not just the lab
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("got your lens order"));

        // and the lab's paperwork says where it goes
        assertThat(LensInquiryService.staffLines(inquiries.findById(id).orElseThrow()))
                .contains("Address: 12 Freedom Way", "Area: Kabulonga", "Landmark: opp. the bank");
    }

    @Test
    void aDoorstepOrderWalksTheLadderAndIsBilledOnDelivery() {
        UUID id = service.start(phone(), "CLEAR", false);
        service.verify(tokenFromLastOutbound());
        service.setDelivery(id, new LensDtos.Delivery("Ann", "12 Freedom Way", "Kabulonga", null));
        service.submit(id);
        assertThat(inquiries.findById(id).orElseThrow().getFulfilment()).isEqualTo("ORDERED");

        // forward one rung at a time: no skipping straight to delivered
        assertThatThrownBy(() -> service.advanceFulfilment(id,
                new LensDtos.AdvanceFulfilment("DELIVERED", "CASH", "Staff B", "Main Store")))
                .hasMessageContaining("one step at a time");

        service.advanceFulfilment(id, new LensDtos.AdvanceFulfilment("PACKED", null, null, null));
        var out = service.advanceFulfilment(id,
                new LensDtos.AdvanceFulfilment("OUT_FOR_DELIVERY", null, null, null));
        assertThat(out.status()).isEqualTo("SUBMITTED"); // still unpaid — cash on delivery
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("out for delivery to 12 Freedom Way"));

        // an in-flight order stays on the staff list the whole way
        assertThat(service.pendingWebOrders()).anySatisfy(v -> assertThat(v.id()).isEqualTo(id));

        var done = service.advanceFulfilment(id,
                new LensDtos.AdvanceFulfilment("DELIVERED", "CASH", "Staff B", "Main Store"));
        assertThat(done.status()).isEqualTo("SOLD"); // delivering it is what bills it
        assertThat(service.pendingWebOrders()).noneSatisfy(v -> assertThat(v.id()).isEqualTo(id));
        assertThat(sentTexts()).anySatisfy(t -> assertThat(t).contains("have been delivered"));
    }

    @Test
    void replayingAWalkInSaleWithTheSameClientReferenceDoesNotDoubleSell() {
        String ref = "device-" + UUID.randomUUID();
        var req = new LensDtos.WalkInSale("Offline Customer", null, "CLEAR", false, null, null,
                "CASH", "Staff A", "Main Store", ref);

        var first = service.walkInSale(req);
        var replay = service.walkInSale(req);

        assertThat(replay.id()).isEqualTo(first.id());
        long matching = service.salesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC)).stream()
                .filter(s -> "Offline Customer".equals(s.customerName())).count();
        assertThat(matching).isEqualTo(1);
    }
}
