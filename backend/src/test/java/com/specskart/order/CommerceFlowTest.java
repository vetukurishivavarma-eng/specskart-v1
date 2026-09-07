package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
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
    @Autowired CartItemRepository cartItems;
    @Autowired StockHoldJob holdJob;
    @Autowired PostPurchaseJob postPurchaseJob;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppProvider whatsapp;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    private Product frame(int priceKwacha, int stock) {
        Product p = new Product();
        p.setSlug("test-frame-" + System.nanoTime());
        p.setName("Test Frame");
        p.setPriceMinor(priceKwacha * 100L);
        p.setStockQty(stock);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    private int stock(UUID id) {
        return products.findById(id).orElseThrow().getStockQty();
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
        assertThat(stock(p.getId())).isEqualTo(3);              // reserved on add-to-cart
        carts.setQty(token, p.getId(), 3);
        assertThat(stock(p.getId())).isEqualTo(2);              // one more reserved

        var withPromo = carts.applyPromo(token, promo.getCode());
        assertThat(withPromo.subtotalMinor()).isEqualTo(240_000);
        assertThat(withPromo.discountMinor()).isEqualTo(48_000);
        assertThat(withPromo.totalMinor()).isEqualTo(192_000);
        assertThat(withPromo.holdExpiresAt()).isAfter(Instant.now());

        var result = checkout.start(token, new OrderDtos.CheckoutRequest(
                "Test Buyer", "260970000001", null, "12 Kabulonga Rd", "Lusaka"));
        assertThat(result.checkoutUrl()).contains("mockPaid=1");
        assertThat(stock(p.getId())).isEqualTo(2);              // no double decrement at checkout
        assertThat(promos.findByCodeIgnoreCase(promo.getCode()).orElseThrow().getRedeemedCount()).isEqualTo(1);

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
    void lastUnitIsHeldForOneShopperOnly() {
        Product p = frame(700, 1);
        carts.addItem(null, p.getId(), 1);                       // shopper A grabs it
        assertThat(stock(p.getId())).isZero();
        assertThatThrownBy(() -> carts.addItem(null, p.getId(), 1)) // shopper B can't
                .hasMessageContaining("sold out");
    }

    @Test
    void removingFromCartReleasesTheHold() {
        Product p = frame(700, 2);
        var cart = carts.addItem(null, p.getId(), 2);
        assertThat(stock(p.getId())).isZero();
        carts.setQty(cart.token(), p.getId(), 0);                // remove
        assertThat(stock(p.getId())).isEqualTo(2);
    }

    @Test
    void expiredHoldIsReleasedByTheJob() {
        Product p = frame(700, 1);
        var cart = carts.addItem(null, p.getId(), 1);
        assertThat(stock(p.getId())).isZero();

        // simulate the 15-min window lapsing
        var line = cartItems.findByCartId(carts.getOrCreate(cart.token()).getId()).get(0);
        line.setHeldUntil(Instant.now().minusSeconds(60));
        cartItems.save(line);

        holdJob.releaseExpired();
        assertThat(stock(p.getId())).isEqualTo(1);
        assertThat(cartItems.findByCartId(carts.getOrCreate(cart.token()).getId())).isEmpty();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void webOrderLinksALeadAndSendsWhatsAppUpdates() {
        Product p = frame(400, 3);
        var cart = carts.addItem(null, p.getId(), 1);
        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Grace Banda", "260971234567", null, "5 Great East Rd", "Lusaka"));

        var lead = leads.findByWhatsappWaId("260971234567").orElseThrow();
        assertThat(lead.getName()).isEqualTo("Grace Banda");

        var mock = (MockWhatsAppProvider) whatsapp;
        int before = mock.outbox().size();
        checkout.confirmPayment(result.orderNo());
        var paidMsg = mock.outbox().subList(before, mock.outbox().size()).stream()
                .filter(s -> s.text() != null && s.text().contains(result.orderNo())).findFirst().orElseThrow();
        assertThat(paidMsg.text()).contains("Grace").contains("Payment received");

        var orderId = orderIdOf(result.orderNo());
        checkout.updateStatus(orderId, OrderStatus.PACKED, null);
        checkout.updateStatus(orderId, OrderStatus.SHIPPED, null);
        checkout.updateStatus(orderId, OrderStatus.DELIVERED, null);

        // backdate the DELIVERED event so the post-purchase job (3-day delay) picks it up
        em.createQuery("update OrderEvent e set e.createdAt = :t where e.orderId = :o and e.status = :s")
                .setParameter("t", java.time.Instant.now().minus(4, java.time.temporal.ChronoUnit.DAYS))
                .setParameter("o", orderId)
                .setParameter("s", OrderStatus.DELIVERED)
                .executeUpdate();
        em.flush();
        em.clear();
        int b2 = mock.outbox().size();
        postPurchaseJob.run();
        assertThat(mock.outbox().subList(b2, mock.outbox().size()))
                .anyMatch(s -> s.text() != null && s.text().toLowerCase().contains("hope you"));
        assertThat(orders.findById(orderId).orElseThrow().getFollowedUpAt()).isNotNull();
    }

    @Test
    void cancelRestocks() {
        Product p = frame(500, 4);
        var cart = carts.addItem(null, p.getId(), 3);
        assertThat(stock(p.getId())).isEqualTo(1);
        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Cancel Me", "260970000002", null, "1 Test Ave", "Ndola"));
        checkout.updateStatus(orderIdOf(result.orderNo()), OrderStatus.CANCELLED, "changed mind");
        assertThat(stock(p.getId())).isEqualTo(4);
    }
}
