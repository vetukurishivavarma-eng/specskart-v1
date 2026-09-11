package com.specskart.order;

import com.specskart.catalog.ProductImage;
import com.specskart.catalog.ProductImageRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import com.specskart.config.AppProperties;
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
 * Every 15 min: a cart with items, a known lead, last touched 1–24h ago and never nudged
 * gets one automatic WhatsApp reminder (inside the 24h service window, so plain text is fine).
 */
@Component
class AbandonedCartJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCartJob.class);

    private final CartRepository carts;
    private final CartItemRepository items;
    private final LeadRepository leads;
    private final PromoCodeRepository promos;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AppProperties props;
    private final ProductImageRepository productImages;

    AbandonedCartJob(CartRepository carts, CartItemRepository items, LeadRepository leads,
                     PromoCodeRepository promos, WhatsAppProvider whatsapp,
                     WhatsAppMessageRepository messages, AppProperties props,
                     ProductImageRepository productImages) {
        this.carts = carts;
        this.items = items;
        this.leads = leads;
        this.promos = promos;
        this.whatsapp = whatsapp;
        this.messages = messages;
        this.props = props;
        this.productImages = productImages;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    @Transactional
    public void nudge() {
        Instant now = Instant.now();
        List<Cart> stale = carts.findByOrderedAtIsNullAndNudgedAtIsNullAndLeadIdIsNotNullAndUpdatedAtBetween(
                now.minus(24, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        for (Cart cart : stale) {
            List<CartItem> lines = items.findByCartId(cart.getId());
            if (lines.isEmpty()) continue;
            Lead lead = leads.findById(cart.getLeadId()).orElse(null);
            if (lead == null) continue;
            String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
            if (waId == null) continue;

            int count = lines.stream().mapToInt(CartItem::getQty).sum();
            String link = props.frontendBaseUrl() + "/store?c=" + cart.getToken();
            StringBuilder msg = new StringBuilder("Still thinking it over? 👓\nYou left ")
                    .append(count).append(count == 1 ? " frame" : " frames").append(" in your bag.");

            PromoCode promo = promos.findByActiveTrueAndAutoIssueTrue().stream()
                    .filter(p -> p.usable(0)).findFirst().orElse(null);
            if (promo != null) {
                msg.append("\n\nHere's ").append(discountText(promo)).append(" — use code *")
                        .append(promo.getCode()).append("* at checkout.");
            }
            msg.append("\n\nPick up where you left off:\n").append(link);

            String heroImage = props.whatsapp().absoluteAsset(firstImageUrl(lines.get(0).getProductId()));

            try {
                if (heroImage != null) whatsapp.sendImage(waId, heroImage, msg.toString());
                else whatsapp.sendText(waId, msg.toString());
                cart.setNudgedAt(now);
                carts.save(cart);
                logOutbound(lead.getId());
                log.info("abandoned-cart nudge sent to lead {}", lead.getId());
            } catch (Exception e) {
                log.warn("abandoned-cart nudge failed for lead {}: {}", lead.getId(), e.getMessage());
            }
        }
    }

    private String firstImageUrl(java.util.UUID productId) {
        if (productId == null) return null;
        return productImages.findByProductIdOrderBySortAsc(productId).stream()
                .findFirst().map(ProductImage::getUrl).orElse(null);
    }

    private String discountText(PromoCode p) {
        return "PERCENT".equals(p.getDiscountType())
                ? p.getDiscountValue() + "% off"
                : OrderNotificationService.money(p.getDiscountValue(), "ZMW") + " off";
    }

    private void logOutbound(java.util.UUID leadId) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType("text");
        m.setBody("abandoned-cart-nudge");
        m.setStatus("sent");
        messages.save(m);
    }
}
