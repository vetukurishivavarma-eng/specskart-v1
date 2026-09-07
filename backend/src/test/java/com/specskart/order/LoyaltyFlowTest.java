package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class LoyaltyFlowTest {

    @Autowired ProductRepository products;
    @Autowired LeadRepository leads;
    @Autowired LeadService leadService;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;
    @Autowired OrderQueryService orderQuery;

    private Product frame(int kwacha, int stock) {
        Product p = new Product();
        p.setSlug("loy-frame-" + System.nanoTime());
        p.setName("Loyalty Frame");
        p.setPriceMinor(kwacha * 100L);
        p.setStockQty(stock);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    private UUID webLead(String phone) {
        return leadService.onWebOrder(phone, "Buyer " + phone).getId();
    }

    @Test
    void earnRedeemAndReferAllWork() {
        // --- referrer with a code ---
        UUID referrer = webLead("260990000001");
        String refCode = leadService.ensureReferralCode(referrer);
        assertThat(refCode).startsWith("SPECS-");
        int referrerStart = leads.findById(referrer).orElseThrow().getPoints();

        // --- friend buys using the referral code ---
        Product p = frame(1000, 5);                                  // K1000
        var cart = carts.addItem(null, p.getId(), 1);
        // link the cart to a fresh lead by giving a phone the friend will also use
        var friendResult = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Friend One", "260990000002", null, "1 Rd", "Lusaka", null, refCode));
        var friendOrder = orders.findByOrderNo(friendResult.orderNo()).orElseThrow();
        // 10% referral discount on K1000 = K100
        assertThat(friendOrder.getDiscountMinor()).isEqualTo(10_000);
        assertThat(friendOrder.getTotalMinor()).isEqualTo(90_000);
        assertThat(friendOrder.getReferredByLeadId()).isEqualTo(referrer);

        checkout.confirmPayment(friendResult.orderNo());

        // referrer got their reward points, friend earned points on the K1000 subtotal
        assertThat(leads.findById(referrer).orElseThrow().getPoints()).isEqualTo(referrerStart + 200);
        Lead friend = leads.findByWhatsappWaId("260990000002").orElseThrow();
        assertThat(friend.getPoints()).isEqualTo(1000);              // 1 pt / K1 on the K1000 subtotal
        assertThat(friend.getReferralCode()).isNotNull();            // friend now has their own code

        // --- friend spends points on a second order ---
        Product p2 = frame(500, 5);
        var cart2 = carts.startForLead(friend.getId());
        carts.addItem(cart2.getToken(), p2.getId(), 1);              // K500
        var r2 = checkout.start(cart2.getToken(), new OrderDtos.CheckoutRequest(
                "Friend One", "260990000002", null, "1 Rd", "Lusaka", 300, null));
        var o2 = orders.findByOrderNo(r2.orderNo()).orElseThrow();
        assertThat(o2.getPointsRedeemed()).isEqualTo(300);           // 300 pts * K0.10 = K30
        assertThat(o2.getDiscountMinor()).isEqualTo(3_000);
        assertThat(o2.getTotalMinor()).isEqualTo(47_000);
        assertThat(leads.findById(friend.getId()).orElseThrow().getPoints()).isEqualTo(700);

        // cancelling restores the redeemed points
        checkout.updateStatus(o2.getId(), OrderStatus.CANCELLED, "test");
        assertThat(leads.findById(friend.getId()).orElseThrow().getPoints()).isEqualTo(1000);

        // order view surfaces the balance + code
        var view = orderQuery.byOrderNo(friendResult.orderNo());
        assertThat(view.pointsBalance()).isGreaterThan(0);
        assertThat(view.referralCode()).isNotNull();
    }
}
