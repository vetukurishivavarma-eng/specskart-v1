package com.specskart.order;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
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
 * A few days after an order is delivered, send one personalised WhatsApp: thanks by name,
 * a tracker link, and a discount code to come back. Sent once per order.
 *
 * If WHATSAPP_POST_PURCHASE_TEMPLATE is set it goes out as that approved template (works
 * outside the 24-hour window); otherwise it's a plain message — delivered only while the
 * customer's service window is still open.
 */
@Component
class PostPurchaseJob {

    private static final Logger log = LoggerFactory.getLogger(PostPurchaseJob.class);
    private static final int DELAY_DAYS = 3;

    private final OrderRepository orders;
    private final OrderEventRepository orderEvents;
    private final LeadRepository leads;
    private final PromoCodeRepository promos;
    private final CartService carts;
    private final WhatsAppProvider whatsapp;
    private final OrderNotificationService notifications;
    private final AnalyticsService analytics;
    private final AppProperties props;

    PostPurchaseJob(OrderRepository orders, OrderEventRepository orderEvents, LeadRepository leads,
                    PromoCodeRepository promos, CartService carts, WhatsAppProvider whatsapp,
                    OrderNotificationService notifications, AnalyticsService analytics, AppProperties props) {
        this.orders = orders;
        this.orderEvents = orderEvents;
        this.leads = leads;
        this.promos = promos;
        this.carts = carts;
        this.whatsapp = whatsapp;
        this.notifications = notifications;
        this.analytics = analytics;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT3M")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(DELAY_DAYS, ChronoUnit.DAYS);
        for (Order order : orders.findByStatusAndFollowedUpAtIsNullAndLeadIdIsNotNull(OrderStatus.DELIVERED)) {
            Instant deliveredAt = orderEvents.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                    .filter(e -> e.getStatus() == OrderStatus.DELIVERED)
                    .map(e -> e.getCreatedAt()).findFirst().orElse(null);
            if (deliveredAt == null || deliveredAt.isAfter(cutoff)) continue;
            Lead lead = leads.findById(order.getLeadId()).orElse(null);
            String waId = lead == null ? null
                    : lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
            if (waId == null) { markDone(order); continue; }

            PromoCode promo = promos.findByActiveTrueAndAutoIssueTrue().stream()
                    .filter(p -> p.usable(0)).findFirst().orElse(null);
            String code = promo != null ? promo.getCode() : null;
            String name = OrderNotificationService.firstName(lead).trim();

            try {
                if (props.whatsapp().postPurchaseConfigured()) {
                    whatsapp.sendTemplate(waId, props.whatsapp().postPurchaseTemplate(),
                            props.whatsapp().followUpTemplateLang(),
                            List.of(name.isBlank() ? "there" : name, code != null ? code : ""));
                } else {
                    whatsapp.sendText(waId, plainMessage(lead, order, code));
                }
                notifications.logSent(order.getLeadId(), "order-post-purchase");
                analytics.record(LeadEventType.REVIEW_REQUESTED, order.getLeadId(), null);
                log.info("post-purchase message sent for order {}", order.getOrderNo());
            } catch (Exception e) {
                log.warn("post-purchase message failed for order {}: {}", order.getOrderNo(), e.getMessage());
            }
            markDone(order);
        }
    }

    private String plainMessage(Lead lead, Order order, String code) {
        String hi = OrderNotificationService.firstName(lead);
        String shop = props.frontendBaseUrl() + "/store?c=" + carts.startForLead(lead.getId()).getToken();
        StringBuilder sb = new StringBuilder("Hi").append(hi).append(" 👋\nHope you're loving your new frames!");
        if (code != null) {
            sb.append("\n\nReady for a second pair or a fresh look? Here's a treat — use *").append(code)
                    .append("* for money off your next order:\n").append(shop);
        } else {
            sb.append("\n\nBrowse new styles any time:\n").append(shop);
        }
        sb.append("\n\nTrack this order or ask us anything right here:\n")
                .append(props.frontendBaseUrl()).append("/order/").append(order.getOrderNo());
        return sb.toString();
    }

    private void markDone(Order order) {
        order.setFollowedUpAt(Instant.now());
        orders.save(order);
    }
}
