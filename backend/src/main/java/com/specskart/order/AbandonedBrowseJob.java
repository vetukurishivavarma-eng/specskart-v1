package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductImage;
import com.specskart.catalog.ProductImageRepository;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.ProductView;
import com.specskart.catalog.ProductViewRepository;
import com.specskart.config.AppProperties;
import com.specskart.lead.FollowUpState;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A known lead looks at a product and doesn't buy -> one WhatsApp nudge for that specific
 * product, 3-96h later (long enough to not feel like they're being watched, short enough
 * to still be relevant). Skipped if they've placed any order since the view — a purchase
 * of anything is a strong enough signal they're not still deciding on this one.
 */
@Component
class AbandonedBrowseJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonedBrowseJob.class);

    private final ProductViewRepository views;
    private final LeadRepository leads;
    private final ProductRepository products;
    private final ProductImageRepository productImages;
    private final OrderRepository orders;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AppProperties props;

    AbandonedBrowseJob(ProductViewRepository views, LeadRepository leads, ProductRepository products,
                       ProductImageRepository productImages, OrderRepository orders,
                       WhatsAppProvider whatsapp, WhatsAppMessageRepository messages, AppProperties props) {
        this.views = views;
        this.leads = leads;
        this.products = products;
        this.productImages = productImages;
        this.orders = orders;
        this.whatsapp = whatsapp;
        this.messages = messages;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT6M")
    @Transactional
    public void nudge() {
        Instant now = Instant.now();
        List<ProductView> due = views.findTop50ByNotifiedAtIsNullAndViewedAtBetweenOrderByViewedAtAsc(
                now.minus(96, ChronoUnit.HOURS), now.minus(3, ChronoUnit.HOURS));
        int sent = 0;
        for (ProductView v : due) {
            v.setNotifiedAt(now); // mark attempted regardless, so a failure never retries into a repeat message
            views.save(v);
            try {
                if (send(v)) sent++;
            } catch (Exception e) {
                log.warn("browse-recovery failed for view {}: {}", v.getId(), e.getMessage());
            }
        }
        if (!due.isEmpty()) log.info("abandoned-browse pass: {} of {} nudged", sent, due.size());
    }

    private boolean send(ProductView v) {
        Lead lead = leads.findById(v.getLeadId()).orElse(null);
        if (lead == null || lead.getFollowUpState() == FollowUpState.OPTED_OUT) return false;
        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        if (waId == null) return false;

        boolean actedSince = orders.findByLeadIdOrderByCreatedAtDesc(lead.getId()).stream()
                .anyMatch(o -> o.getCreatedAt().isAfter(v.getViewedAt()));
        if (actedSince) return false;

        Product p = products.findById(v.getProductId()).filter(Product::isActive).orElse(null);
        if (p == null || !p.inStock()) return false;

        String link = props.frontendBaseUrl() + "/store/" + p.getSlug();
        String msg = "Still thinking about the *" + p.getName() + "*? 👀\n"
                + OrderNotificationService.money(p.getPriceMinor(), p.getCurrency())
                + " — here it is, ready when you are:\n" + link;
        String hero = props.whatsapp().absoluteAsset(firstImageUrl(p.getId()));

        if (hero != null) whatsapp.sendImage(waId, hero, msg); else whatsapp.sendText(waId, msg);
        logOutbound(lead.getId());
        return true;
    }

    private String firstImageUrl(java.util.UUID productId) {
        return productImages.findByProductIdOrderBySortAsc(productId).stream()
                .findFirst().map(ProductImage::getUrl).orElse(null);
    }

    private void logOutbound(java.util.UUID leadId) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType("image");
        m.setBody("abandoned-browse-nudge");
        m.setStatus("sent");
        messages.save(m);
    }
}
