package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** A paid order pings every configured staff WhatsApp number, once. */
@SpringBootTest
@ActiveProfiles("mock")
@TestPropertySource(properties = "specskart.whatsapp.staff-numbers=+260999000111, +260888000222")
class StaffOrderAlertTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired WhatsAppProvider whatsapp;

    @Test
    void paidOrderAlertsEveryStaffNumber() {
        Product p = new Product();
        p.setSlug("staff-alert-frame-" + System.nanoTime());
        p.setName("Test Frame");
        p.setPriceMinor(500_00L);
        p.setStockQty(3);
        p.setStatus("ACTIVE");
        p = products.save(p);

        var cart = carts.addItem(null, p.getId(), 1);
        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Grace Banda", "260971234567", null, "5 Great East Rd", "Lusaka", null, null));

        var mock = (MockWhatsAppProvider) whatsapp;
        int before = mock.outbox().size();
        checkout.confirmPayment(result.orderNo());
        // idempotent — a second confirm must not double the alert
        checkout.confirmPayment(result.orderNo());

        var alerts = mock.outbox().subList(before, mock.outbox().size()).stream()
                .filter(s -> s.text() != null && s.text().contains("New order " + result.orderNo()))
                .toList();

        assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting(MockWhatsAppProvider.Sent::toWaId)
                .containsExactlyInAnyOrder("+260999000111", "+260888000222");
        assertThat(alerts.get(0).text()).contains("Grace Banda").contains("Pack & dispatch");
    }
}
