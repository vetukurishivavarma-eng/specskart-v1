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

/** Turns order status changes into automatic WhatsApp updates. No agent involved. */
@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    private final LeadRepository leads;
    private final OrderItemRepository items;
    private final OrderEventRepository orderEvents;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AppProperties props;

    public OrderNotificationService(LeadRepository leads, OrderItemRepository items, OrderEventRepository orderEvents,
                                    WhatsAppProvider whatsapp, WhatsAppMessageRepository messages, AppProperties props) {
        this.leads = leads;
        this.items = items;
        this.orderEvents = orderEvents;
        this.whatsapp = whatsapp;
        this.messages = messages;
        this.props = props;
    }

    public static String money(long minor, String currency) {
        return currency + " " + String.format("%,.2f", minor / 100.0);
    }

    public void onStatus(Order order, OrderStatus status) {
        String line = statusLine(status);
        if (line == null) return; // status with no customer-facing message
        if (order.getLeadId() == null) return;
        Lead lead = leads.findById(order.getLeadId()).orElse(null);
        if (lead == null) return;
        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        if (waId == null) return;

        String hi = firstName(lead);
        String track = props.frontendBaseUrl() + "/order/" + order.getOrderNo();
        String logKey = "order-" + status.name().toLowerCase();

        try {
            if (props.whatsapp().orderUpdateConfigured()) {
                // Approved template — the only thing Meta delivers outside the customer's 24h window.
                whatsapp.sendTemplate(waId, props.whatsapp().orderUpdateTemplate(),
                        props.whatsapp().followUpTemplateLang(),
                        List.of(hi.isBlank() ? "there" : hi.trim(), line, order.getOrderNo(), track));
            } else {
                whatsapp.sendText(waId, plainMessage(order, status, lead, hi, track));
            }
            logOutbound(order.getLeadId(), logKey, "sent");
        } catch (Exception e) {
            log.warn("{} WhatsApp send failed for order {}: {}", logKey, order.getOrderNo(), e.getMessage());
            logOutbound(order.getLeadId(), logKey, "failed");
            recordFailure(order, "Customer WhatsApp (" + status.name() + ") not delivered", e);
        }
    }

    /**
     * A WhatsApp to every configured staff number the moment an order is paid — the one
     * back-office cue that a box needs packing. Best-effort per number; a failure is logged
     * and also written to the order timeline so it is visible in admin.
     */
    public void notifyNewOrder(Order order) {
        List<String> staff = props.whatsapp().staffNumbers();
        if (staff.isEmpty()) return;

        String who = order.getCustomerName() != null && !order.getCustomerName().isBlank()
                ? order.getCustomerName() : "Guest";
        if (order.getCustomerPhone() != null) who += " · " + order.getCustomerPhone();
        String amount = money(order.getTotalMinor(), order.getCurrency());
        String adminLink = props.frontendBaseUrl() + "/admin/orders/" + order.getId();
        String pay = order.isCod() ? "\n💵 CASH ON DELIVERY — collect " + amount + " on hand-over" : "";
        String plain = "🛍️ New order " + order.getOrderNo() + " — " + amount + pay + "\n"
                + itemLine(order) + "\n" + who + "\n\nPack & dispatch: " + adminLink;
        boolean useTemplate = props.whatsapp().staffOrderConfigured();

        for (String to : staff) {
            String number = to.trim();
            if (number.isEmpty()) continue;
            try {
                if (useTemplate) {
                    whatsapp.sendTemplate(number, props.whatsapp().staffOrderTemplate(),
                            props.whatsapp().followUpTemplateLang(),
                            List.of(order.getOrderNo(), amount, who, adminLink));
                } else {
                    whatsapp.sendText(number, plain);
                }
            } catch (Exception e) {
                log.warn("staff new-order alert to {} failed for {}: {}", number, order.getOrderNo(), e.getMessage());
                recordFailure(order, "Staff alert to " + mask(number) + " failed", e);
            }
        }
    }

    /** Short, single-line status headline — fills {{2}} of the order-update template. Null = no customer message. */
    static String statusLine(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "Order confirmed — pay cash when it arrives";
            case PAID -> "Payment received — we're preparing your frames";
            case PACKED -> "Packed and ready for dispatch";
            case SHIPPED -> "Handed to the courier — on its way to you";
            case DELIVERED -> "Delivered — enjoy your new frames!";
            case CANCELLED -> "Your order has been cancelled";
            case REFUNDED -> "Your refund has been processed";
            default -> null;
        };
    }

    private String plainMessage(Order order, OrderStatus status, Lead lead, String hi, String track) {
        String items = itemLine(order);
        String rewards = "";
        if (order.getPointsEarned() > 0) {
            rewards = "\n\n⭐ You earned " + order.getPointsEarned() + " points (balance: " + lead.getPoints() + ").";
        }
        if (lead.getReferralCode() != null) {
            rewards += "\nShare code *" + lead.getReferralCode() + "* — your friend gets a discount, you get points.";
        }
        return switch (status) {
            case CONFIRMED -> "Order confirmed" + hi + " ✅\n\nOrder " + order.getOrderNo() + "\n" + items
                    + "\n\nPay *" + money(order.getTotalMinor(), order.getCurrency())
                    + "* in cash to the courier on delivery. We're getting your frames ready — track any time:\n" + track;
            case PAID -> "Thanks" + hi + "! Payment received ✅\n\nOrder " + order.getOrderNo() + "\n" + items
                    + "\nTotal " + money(order.getTotalMinor(), order.getCurrency()) + rewards
                    + "\n\nWe're preparing your frames. Track your order any time:\n" + track;
            case PACKED -> "Good news" + hi + " — order " + order.getOrderNo() + " is packed and ready for dispatch 📦\n" + track;
            case SHIPPED -> "On its way 🛵\nOrder " + order.getOrderNo() + " has been handed to the courier — "
                    + "expected within a few working days.\n" + track;
            case DELIVERED -> "Delivered 🎉 Enjoy your new frames" + hi + "!\nAnything not right with the fit? Just reply here.";
            case CANCELLED -> "Order " + order.getOrderNo() + " has been cancelled. If this wasn't you, reply here.";
            case REFUNDED -> "A refund for order " + order.getOrderNo() + " has been processed.";
            default -> null;
        };
    }

    /** Record an outbound message on the lead's WhatsApp thread (the send happened elsewhere). */
    public void logSent(java.util.UUID leadId, String key) {
        logOutbound(leadId, key, "sent");
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

    /** Put a failed WhatsApp send on the order timeline so it shows in admin (⚠ prefix = alert). */
    private void recordFailure(Order order, String what, Exception e) {
        try {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String note = "⚠ " + what + ": " + reason;
            OrderEvent ev = new OrderEvent();
            ev.setOrderId(order.getId());
            ev.setStatus(order.getStatus());
            ev.setNote(note.length() > 500 ? note.substring(0, 500) : note);
            orderEvents.save(ev);
        } catch (Exception ex) {
            log.warn("could not record notification failure for order {}: {}", order.getOrderNo(), ex.getMessage());
        }
    }

    private static String mask(String number) {
        return number.length() <= 4 ? number : number.substring(0, number.length() - 4) + "••••";
    }

    private void logOutbound(java.util.UUID leadId, String body, String status) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType("text");
        m.setBody(body);
        m.setStatus(status);
        messages.save(m);
    }
}
