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
}
