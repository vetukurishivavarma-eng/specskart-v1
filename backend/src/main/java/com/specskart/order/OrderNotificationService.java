package com.specskart.order;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Turns order status changes into automatic, personalised WhatsApp updates. No agent involved. */
@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    private final LeadRepository leads;
    private final OrderItemRepository items;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AppProperties props;

    public OrderNotificationService(LeadRepository leads, OrderItemRepository items, WhatsAppProvider whatsapp,
                                    WhatsAppMessageRepository messages, AppProperties props) {
        this.leads = leads;
        this.items = items;
        this.whatsapp = whatsapp;
        this.messages = messages;
        this.props = props;
    }

    public static String money(long minor, String currency) {
        return currency + " " + String.format("%,.2f", minor / 100.0);
    }

    public void onStatus(Order order, OrderStatus status) {
        if (order.getLeadId() == null) return;
        Lead lead = leads.findById(order.getLeadId()).orElse(null);
        if (lead == null) return;
        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        if (waId == null) return;

        String hi = firstName(lead);
        String track = props.frontendBaseUrl() + "/order/" + order.getOrderNo();
        String items = itemLine(order);

        String rewards = "";
        if (order.getPointsEarned() > 0) {
            rewards = "\n\n⭐ You earned " + order.getPointsEarned() + " points (balance: " + lead.getPoints() + ").";
        }
        if (lead.getReferralCode() != null) {
            rewards += "\nShare code *" + lead.getReferralCode() + "* — your friend gets a discount, you get points.";
        }

        String msg = switch (status) {
            case PAID -> "Thanks" + hi + "! Payment received ✅\n\nOrder " + order.getOrderNo() + "\n" + items
                    + "\nTotal " + money(order.getTotalMinor(), order.getCurrency())
                    + rewards
                    + "\n\nWe're preparing your frames. Track your order any time:\n" + track;
            case PACKED -> "Good news" + hi + " — order " + order.getOrderNo() + " is packed and ready for dispatch 📦\n" + track;
            case SHIPPED -> "On its way 🛵\nOrder " + order.getOrderNo() + " has been handed to the courier — "
                    + "expected within a few working days.\n" + track;
            case DELIVERED -> "Delivered 🎉 Enjoy your new frames" + hi + "!\nAnything not right with the fit? Just reply here.";
            case CANCELLED -> "Order " + order.getOrderNo() + " has been cancelled. If this wasn't you, reply here.";
            case REFUNDED -> "A refund for order " + order.getOrderNo() + " has been processed.";
            default -> null;
        };
        if (msg == null) return;
        send(order.getLeadId(), waId, msg, "order-" + status.name().toLowerCase());
    }

    /**
     * A plain WhatsApp to every configured staff number the moment an order is
     * paid — the one back-office cue that a box needs packing. Best-effort: a
     * number that fails is logged and the rest still go. Not recorded on any
     * lead thread; the recipients aren't leads.
     */
    public void notifyNewOrder(Order order) {
        List<String> staff = props.whatsapp().staffNumbers();
        if (staff.isEmpty()) return;

        String who = order.getCustomerName() != null && !order.getCustomerName().isBlank()
                ? order.getCustomerName() : "Guest";
        String phone = order.getCustomerPhone() != null ? " · " + order.getCustomerPhone() : "";
        String msg = "🛍️ New order " + order.getOrderNo() + " — "
                + money(order.getTotalMinor(), order.getCurrency()) + "\n"
                + itemLine(order) + "\n" + who + phone
                + "\n\nPack & dispatch: " + props.frontendBaseUrl() + "/admin/orders/" + order.getId();

        for (String to : staff) {
            String number = to.trim();
            if (number.isEmpty()) continue;
            try {
                whatsapp.sendText(number, msg);
            } catch (Exception e) {
                log.warn("staff new-order alert to {} failed for {}: {}", number, order.getOrderNo(), e.getMessage());
            }
        }
    }

    void send(java.util.UUID leadId, String waId, String text, String logKey) {
        try {
            whatsapp.sendText(waId, text);
            logOutbound(leadId, logKey);
        } catch (Exception e) {
            log.warn("{} WhatsApp send failed for lead {}: {}", logKey, leadId, e.getMessage());
        }
    }

    /** Record an outbound message on the lead's WhatsApp thread (the send happened elsewhere). */
    public void logSent(java.util.UUID leadId, String key) {
        logOutbound(leadId, key);
    }

    static String firstName(Lead lead) {
        if (lead.getName() == null || lead.getName().isBlank()) return "";
        return " " + lead.getName().trim().split("\\s+")[0];
    }

    private String itemLine(Order order) {
        List<OrderItem> lines = items.findByOrderId(order.getId());
        return lines.stream()
                .map(i -> "• " + i.getProductName() + (i.getQty() > 1 ? " ×" + i.getQty() : ""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private void logOutbound(java.util.UUID leadId, String body) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType("text");
        m.setBody(body);
        m.setStatus("sent");
        messages.save(m);
    }
}
