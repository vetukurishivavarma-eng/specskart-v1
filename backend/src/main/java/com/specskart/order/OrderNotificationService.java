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

/** Turns order status changes into automatic WhatsApp updates. No agent involved. */
@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    private final LeadRepository leads;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AppProperties props;

    public OrderNotificationService(LeadRepository leads, WhatsAppProvider whatsapp,
                                    WhatsAppMessageRepository messages, AppProperties props) {
        this.leads = leads;
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

        String track = props.frontendBaseUrl() + "/order/" + order.getOrderNo();
        String msg = switch (status) {
            case PAID -> "Payment received ✅\nOrder " + order.getOrderNo() + " — "
                    + money(order.getTotalMinor(), order.getCurrency())
                    + "\nWe're preparing your frames. Track it here:\n" + track;
            case PACKED -> "Your order " + order.getOrderNo() + " is packed and ready for dispatch 📦\n" + track;
            case SHIPPED -> "On its way 🛵\nOrder " + order.getOrderNo() + " has been handed to the courier — "
                    + "expected in " + deliveryEta() + ".\n" + track;
            case DELIVERED -> "Delivered 🎉 Enjoy your new frames!\nAny issue with the fit? Just reply here.";
            case CANCELLED -> "Order " + order.getOrderNo() + " has been cancelled. If this wasn't you, reply here.";
            case REFUNDED -> "A refund for order " + order.getOrderNo() + " has been processed.";
            default -> null;
        };
        if (msg == null) return;
        try {
            whatsapp.sendText(waId, msg);
            logOutbound(order.getLeadId(), "order-" + status.name().toLowerCase());
        } catch (Exception e) {
            log.warn("order {} status {} WhatsApp notify failed: {}", order.getOrderNo(), status, e.getMessage());
        }
    }

    private String deliveryEta() {
        return "a few working days";
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
