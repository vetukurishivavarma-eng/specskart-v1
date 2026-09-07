package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class CommerceFlowTest {

    @Autowired ProductRepository products;
    @Autowired PromoCodeRepository promos;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderQueryService orderQuery;
    @Autowired OrderRepository orders;

    private Product frame(int priceKwacha, int stock) {
        Product p = new Product();
        p.setSlug("test-frame-" + System.nanoTime());
        p.setName("Test Frame");
        p.setPriceMinor(priceKwacha * 100L);
        p.setStockQty(stock);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    private UUID orderIdOf(String orderNo) {
        return orders.findByOrderNo(orderNo).orElseThrow().getId();
    }

    @Test
    void cartToPaidOrderRunsWithoutAHuman() {
        Product p = frame(800, 5);
        PromoCode promo = new PromoCode();
        promo.setCode("TEST20-" + System.nanoTime());
        promo.setDiscountType("PERCENT");
        promo.setDiscountValue(20);
        promos.save(promo);

        var cart = carts.addItem(null, p.getId(), 2);
        String token = cart.token();
        carts.setQty(token, p.getId(), 3);                    // 3 x K800 = K2400
        var withPromo = carts.applyPromo(token, promo.getCode());
        assertThat(withPromo.subtotalMinor()).isEqualTo(240_000);
        assertThat(withPromo.discountMinor()).isEqualTo(48_000);
        assertThat(withPromo.totalMinor()).isEqualTo(192_000);

        var result = checkout.start(token, new OrderDtos.CheckoutRequest(
                "Test Buyer", "260970000001", null, "12 Kabulonga Rd", "Lusaka"));
        assertThat(result.checkoutUrl()).contains("mockPaid=1");
        assertThat(products.findById(p.getId()).orElseThrow().getStockQty()).isEqualTo(2);
        assertThat(promos.findByCodeIgnoreCase(promo.getCode()).orElseThrow().getRedeemedCount()).isEqualTo(1);
        assertThat(orderQuery.byOrderNo(result.orderNo()).status()).isEqualTo("PENDING_PAYMENT");

        checkout.confirmPayment(result.orderNo());
        checkout.confirmPayment(result.orderNo()); // idempotent

        var paid = orderQuery.byOrderNo(result.orderNo());
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.timeline()).extracting(OrderDtos.StatusEvent::status)
                .containsExactly("PENDING_PAYMENT", "PAID");

        UUID id = orderIdOf(result.orderNo());
        checkout.updateStatus(id, OrderStatus.PACKED, "boxed");
        checkout.updateStatus(id, OrderStatus.SHIPPED, null);
        checkout.updateStatus(id, OrderStatus.DELIVERED, null);
        assertThat(orderQuery.byOrderNo(result.orderNo()).status()).isEqualTo("DELIVERED");

        assertThatThrownBy(() -> checkout.updateStatus(id, OrderStatus.PENDING_PAYMENT, null))
                .hasMessageContaining("Can't move");
    }

    @Test
    void cancelRestocks() {
        Product p = frame(500, 4);
        var cart = carts.addItem(null, p.getId(), 3);
        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Cancel Me", "260970000002", null, "1 Test Ave", "Ndola"));
        assertThat(products.findById(p.getId()).orElseThrow().getStockQty()).isEqualTo(1);
        checkout.updateStatus(orderIdOf(result.orderNo()), OrderStatus.CANCELLED, "changed mind");
        assertThat(products.findById(p.getId()).orElseThrow().getStockQty()).isEqualTo(4);
    }

    @Test
    void outOfStockIsRejectedAtCheckout() {
        Product p = frame(600, 1);
        var cart = carts.addItem(null, p.getId(), 1);
        // drop stock to 0 behind the cart's back
        p.setStockQty(0);
        products.save(p);
        assertThatThrownBy(() -> checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "No Stock", "260970000003", null, "9 Test St", "Kitwe")))
                .hasMessageContaining("sold out");
    }
}
