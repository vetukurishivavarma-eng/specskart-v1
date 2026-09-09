package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

/** Order-status updates go out as the approved template, and a failed send lands on the order timeline. */
@SpringBootTest
@ActiveProfiles("mock")
@TestPropertySource(properties = {
        "specskart.whatsapp.order-update-template=order_update_v1",
        "specskart.whatsapp.follow-up-template-lang=en",
})
class OrderNotificationTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderQueryService orders;
    @MockitoBean WhatsAppProvider whatsapp;

    private String placeAndPay() {
        Product p = new Product();
        p.setSlug("notif-frame-" + System.nanoTime());
        p.setName("Notif Frame");
        p.setPriceMinor(400_00L);
        p.setStockQty(3);
        p.setStatus("ACTIVE");
        p = products.save(p);
        var cart = carts.addItem(null, p.getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Grace Banda", "260971234567", null, "5 Great East Rd", "Lusaka", null, null));
        checkout.confirmPayment(res.orderNo());
        return res.orderNo();
    }

    @Test
    void paidOrderGoesOutAsTemplate() {
        String orderNo = placeAndPay();

        Mockito.verify(whatsapp).sendTemplate(eq("260971234567"), eq("order_update_v1"), eq("en"),
                argThat(params -> params.size() == 4
                        && params.get(0).equals("Grace")
                        && params.get(1).contains("Payment received")
                        && params.get(2).equals(orderNo)
                        && params.get(3).endsWith("/order/" + orderNo)));
        Mockito.verify(whatsapp, Mockito.never()).sendText(anyString(), anyString());
    }

    @Test
    void failedSendIsRecordedOnTheTimeline() {
        Mockito.doThrow(new RuntimeException("(#131047) re-engagement message"))
                .when(whatsapp).sendTemplate(anyString(), anyString(), anyString(), anyList());

        String orderNo = placeAndPay();

        List<OrderDtos.StatusEvent> timeline = orders.byOrderNo(orderNo).timeline();
        assertThat(timeline).anyMatch(e -> e.note() != null
                && e.note().startsWith("⚠")
                && e.note().contains("131047"));
    }
}
