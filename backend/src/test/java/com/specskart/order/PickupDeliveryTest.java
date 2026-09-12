package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class PickupDeliveryTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderQueryService orderQuery;

    private Product frame() {
        Product p = new Product();
        p.setSlug("pickup-frame-" + System.nanoTime());
        p.setName("Pickup Frame");
        p.setPriceMinor(50_000);
        p.setStockQty(3);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    @Test
    void busStationPickupIsRecordedOnTheOrder() {
        var cart = carts.addItem(null, frame().getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Mutale B", "260955111222", null, "unused for pickup", "Kitwe",
                null, null, false, "PICKUP", "Power Tools, Kitwe"));

        var view = orderQuery.byOrderNo(res.orderNo());
        assertThat(view.deliveryMethod()).isEqualTo("PICKUP");
        assertThat(view.pickupPoint()).isEqualTo("Power Tools, Kitwe");
    }

    @Test
    void defaultsToDoorDeliveryWhenNoPickupPointGiven() {
        var cart = carts.addItem(null, frame().getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Door Buyer", "260955333444", null, "5 Cairo Rd", "Lusaka",
                null, null, false, "PICKUP", null)); // asked for pickup but gave no point

        var view = orderQuery.byOrderNo(res.orderNo());
        assertThat(view.deliveryMethod()).isEqualTo("DOOR");
    }
}
