package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.StaffAlert;
import com.specskart.whatsapp.StaffAlertRepository;
import com.specskart.whatsapp.StaffAlerts;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** A staff alert Meta rejects, or reports as undelivered, is retried until it is delivered. */
@SpringBootTest
@ActiveProfiles("mock")
@TestPropertySource(properties = "specskart.whatsapp.staff-numbers=+260977000555")
class StaffAlertRetryTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;
    @Autowired WhatsAppProvider whatsapp;
    @Autowired StaffAlerts staffAlerts;
    @Autowired StaffAlertRepository alerts;

    @Test
    void failedAlertIsRetriedUntilDelivered() {
        Product p = new Product();
        p.setSlug("staff-retry-frame-" + System.nanoTime());
        p.setName("Retry Frame");
        p.setPriceMinor(400_00L);
        p.setStockQty(3);
        p.setStatus("ACTIVE");
        p = products.save(p);
        var cart = carts.addItem(null, p.getId(), 1);
        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Mutale Phiri", "260971112233", null, "Plot 9 Cairo Rd", "Lusaka", null, null));
        var mock = (MockWhatsAppProvider) whatsapp;

        // 1. Meta rejects the first send: the alert stays queued instead of vanishing.
        //    (2 failures: the customer's own "payment received" message goes out first.)
        mock.failNext(2);
        checkout.confirmPayment(result.orderNo());
        Order order = orders.findByOrderNo(result.orderNo()).orElseThrow();
        StaffAlert a = alerts.findByKindAndRefId("ORDER", order.getId()).get(0);
        assertThat(a.getStatus()).isEqualTo(StaffAlert.Status.PENDING);
        assertThat(a.getAttempts()).isEqualTo(1);
        assertThat(sentFor(mock, result.orderNo())).isZero();

        // 2. The retry job sends it.
        a = due(a);
        staffAlerts.retryDue();
        a = alerts.findById(a.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(StaffAlert.Status.SENT);
        assertThat(a.getWamid()).isNotBlank();
        assertThat(sentFor(mock, result.orderNo())).isEqualTo(1);

        // 3. Meta later reports it undelivered: queued again, and resent.
        staffAlerts.onDeliveryStatus(a.getWamid(), "failed", "131047 Re-engagement message");
        a = alerts.findById(a.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(StaffAlert.Status.PENDING);
        a = due(a);
        staffAlerts.retryDue();
        a = alerts.findById(a.getId()).orElseThrow();
        assertThat(sentFor(mock, result.orderNo())).isEqualTo(2);

        // 4. Delivered closes it: nothing more goes out, even when the job runs again.
        staffAlerts.onDeliveryStatus(a.getWamid(), "delivered", " ");
        a = alerts.findById(a.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(StaffAlert.Status.DELIVERED);
        staffAlerts.retryDue();
        assertThat(sentFor(mock, result.orderNo())).isEqualTo(2);

        // 5. A second confirm of the same order queues nothing new.
        checkout.confirmPayment(result.orderNo());
        assertThat(alerts.findByKindAndRefId("ORDER", order.getId())).hasSize(1);
    }

    private StaffAlert due(StaffAlert a) {
        a.setNextAttemptAt(Instant.now().minusSeconds(1));
        return alerts.save(a);
    }

    private static long sentFor(MockWhatsAppProvider mock, String orderNo) {
        return mock.outbox().stream()
                .filter(s -> "+260977000555".equals(s.toWaId()))
                .filter(s -> s.text() != null && s.text().contains("New order " + orderNo))
                .count();
    }
}
