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

    /** No delivery since 2026-10-21: no address is asked for, and every order is collected at the shop. */
    @Test
    void anOrderNeedsNoAddressAndIsCollectedAtTheShop() {
        var cart = carts.addItem(null, frame().getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Mutale B", "260955111222", null, null, null,
                null, null, true, null, null));

        var view = orderQuery.byOrderNo(res.orderNo());
        assertThat(view.deliveryMethod()).isEqualTo("STORE");
        assertThat(view.shipAddress()).startsWith("Collect at");
    }

    @Test
    void anOldPickupOrDoorRequestIsStillCollectedAtTheShop() {
        var cart = carts.addItem(null, frame().getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Door Buyer", "260955333444", null, "5 Cairo Rd", "Kitwe",
                null, null, false, "PICKUP", "Power Tools, Kitwe")); // a browser still on the old page

        assertThat(orderQuery.byOrderNo(res.orderNo()).deliveryMethod()).isEqualTo("STORE");
    }
}
