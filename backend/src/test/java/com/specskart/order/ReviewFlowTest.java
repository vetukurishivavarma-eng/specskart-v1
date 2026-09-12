package com.specskart.order;

import com.specskart.catalog.CatalogService;
import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.ReviewRepository;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.WhatsAppInboundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class ReviewFlowTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;
    @Autowired ReviewCaptureService reviewCapture;
    @Autowired ReviewRepository reviews;
    @Autowired CatalogService catalog;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppInboundService inbound;

    private Product frame() {
        Product p = new Product();
        p.setSlug("review-frame-" + System.nanoTime());
        p.setName("Reviewable Frame");
        p.setPriceMinor(70_000);
        p.setStockQty(3);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    @Test
    void aBareDigitReplyIsCapturedAsARatingOnlyWhenOneIsPending() {
        String waId = "26097" + (400000 + (int) (Math.random() * 500000));
        Product p = frame();
        var cart = carts.addItem(null, p.getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Reviewer", waId, null, "1 Cairo Rd", "Lusaka", null, null));
        UUID orderId = orders.findByOrderNo(res.orderNo()).orElseThrow().getId();
        UUID leadId = orders.findById(orderId).orElseThrow().getLeadId();

        // no pending review yet -> a bare "5" falls through to the ordinary welcome menu,
        // not captured as a rating
        inbound.process(new InboundMessage(waId, waId, "Reviewer", "5", null, "r0", Map.of()));
        assertThat(reviews.findAll()).noneMatch(r -> r.getOrderId().equals(orderId));

        reviewCapture.markPending(leadId, orderId);
        inbound.process(new InboundMessage(waId, waId, "Reviewer", "5", null, "r1", Map.of()));

        assertThat(reviews.findAll()).anyMatch(r -> r.getOrderId().equals(orderId)
                && r.getProductId().equals(p.getId()) && r.getRating() == 5);
        assertThat(leads.findById(leadId).orElseThrow().getProviderMetadata()).doesNotContainKey("pendingReviewOrderId");

        var detail = catalog.detail(p.getSlug());
        assertThat(detail.avgRating()).isEqualTo(5.0);
        assertThat(detail.reviewCount()).isEqualTo(1);
    }
}
