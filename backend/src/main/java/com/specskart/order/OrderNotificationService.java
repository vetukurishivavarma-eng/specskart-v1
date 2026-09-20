package com.specskart.order;

import com.specskart.config.AppProperties;
import com.specskart.shared.SignedLinks;
import com.specskart.whatsapp.StaffAlerts;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns order status changes into automatic WhatsApp updates. No agent involved. */
@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);
    // ponytail: the business is Zambia-only; make this a property if a second country opens.
    public static final DateTimeFormatter STAFF_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Africa/Lusaka"));

    private final LeadRepository leads;
    private final OrderItemRepository items;
    private final OrderEventRepository orderEvents;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AppProperties props;
    private final StaffAlerts staffAlerts;
    private final SignedLinks links;
    private final PrescriptionFileRepository prescriptionFiles;
    private final com.specskart.pos.StoreRepository stores;

    public OrderNotificationService(LeadRepository leads, OrderItemRepository items, OrderEventRepository orderEvents,
                                    WhatsAppProvider whatsapp, WhatsAppMessageRepository messages, AppProperties props,
                                    StaffAlerts staffAlerts, SignedLinks links, PrescriptionFileRepository prescriptionFiles,
                                    com.specskart.pos.StoreRepository stores) {
        this.stores = stores;
        this.staffAlerts = staffAlerts;
        this.links = links;
        this.prescriptionFiles = prescriptionFiles;
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
        if (props.whatsapp().staffNumbers().isEmpty()) return;
        String no = order.getOrderNo();
        String who = order.getCustomerName() == null || order.getCustomerName().isBlank() ? "Guest" : order.getCustomerName();

        // Meta fetches the PDF from this API, so it needs the API's public origin (asset-base-url).
        String base = props.whatsapp().absoluteAsset("/api/public/staff-docs/orders/" + no);
        String pdfUrl = base == null ? null : base + ".pdf" + links.query("orders/" + no, StaffDocController.TTL);
        StaffAlerts.Attachment rx = null;
        if (base != null && order.getPrescriptionFileId() != null) {
            PrescriptionFile f = prescriptionFiles.findById(order.getPrescriptionFileId()).orElse(null);
            if (f != null) {
                boolean pdf = f.getContentType().contains("pdf");
                rx = new StaffAlerts.Attachment(
                        base + "/prescription" + links.query("orders/" + no + "/prescription", StaffDocController.TTL),
                        "prescription-" + no + ".pdf", !pdf);
            }
        }
        // {{4}} opens the POS app on this order's Deliveries row. The web admin link stays on the
        // slip itself (see staffLines) for whoever is at a desk rather than on the shop floor.
        String appLink = props.whatsapp().absoluteAsset("/api/public/open/order/" + order.getId());
        List<String> failures = staffAlerts.send(staffLines(order), pdfUrl, no + ".pdf",
                List.of(no, money(order.getTotalMinor(), order.getCurrency()), who,
                        appLink == null ? adminLink(order) : appLink), rx);
        for (String failure : failures) recordNote(order, "Staff alert to " + failure);
    }

    /** Everything the packer and the lens lab need, one fact per line — drawn into the staff PDF
     *  and used as the WhatsApp caption ("# " = heading, "` " = monospace). */
    public List<String> staffLines(Order order) {
        String cur = order.getCurrency();
        String total = money(order.getTotalMinor(), cur);
        List<String> out = new ArrayList<>();
        out.add("# 🛍️ New order " + order.getOrderNo() + " — " + total);
        out.add("Placed: " + STAFF_TIME.format(order.getCreatedAt()));
        out.add(order.isCod()
                ? "💵 CASH ON DELIVERY — collect " + total + " on hand-over"
                : "Payment: " + (order.getPaidAt() != null ? "PAID online" : "not paid yet")
                        + (order.getPaymentProvider() == null ? "" : " (" + order.getPaymentProvider() + ")"));
        out.add("");
        out.add("# Customer");
        out.add("Name: " + orDash(order.getCustomerName()));
        out.add("Phone: " + orDash(order.getCustomerPhone()));
        if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) out.add("Email: " + order.getCustomerEmail());
        out.add("PICKUP".equals(order.getDeliveryMethod())
                ? "Pickup at: " + orDash(order.getPickupPoint())
                : "Deliver to: " + orDash(order.getShipAddress()) + ", " + orDash(order.getShipCity()));
        com.specskart.pos.Store from = order.getFulfilStoreId() == null ? null
                : stores.findById(order.getFulfilStoreId()).orElse(null);
        if (from != null) out.add("🏬 Ships from: " + from.getName());
        if (order.getDeliveryLat() != null && order.getDeliveryLng() != null) {
            String to = order.getDeliveryLat() + "," + order.getDeliveryLng();
            out.add("📍 Customer's pin: https://www.google.com/maps/search/?api=1&query=" + to);
            // Google Maps URLs are free (no API key): tapping shows the real road route and distance
            if (from != null && from.getLatitude() != null && from.getLongitude() != null) {
                out.add("🧭 Route from the shop: https://www.google.com/maps/dir/?api=1&origin="
                        + from.getLatitude() + "," + from.getLongitude() + "&destination=" + to + "&travelmode=driving");
            }
        }
        out.add("");
        out.add("# Items");
        for (OrderItem i : items.findByOrderId(order.getId())) {
            out.add("• " + i.getProductName() + " ×" + i.getQty() + " @ " + money(i.getUnitPriceMinor(), cur)
                    + (i.getStoreId() != null && !i.getStoreId().equals(order.getFulfilStoreId())
                        ? " — from " + shopName(i.getStoreId()) : ""));
        }
        if (order.getLensType() != null) {
            out.add("Lenses: " + label(order.getLensType())
                    + (order.getLensAddMinor() > 0 ? " (+" + money(order.getLensAddMinor(), cur) + ")" : ""));
        }
        if (order.getDiscountMinor() > 0) {
            out.add("Discount: -" + money(order.getDiscountMinor(), cur)
                    + (order.getPromoCode() == null ? "" : " (" + order.getPromoCode() + ")"));
        }
        if (order.getShippingMinor() > 0) out.add("Delivery fee: " + money(order.getShippingMinor(), cur));
        out.add("Total: " + total);
        if (order.getLensType() != null && !"NON_PRESCRIPTION".equals(order.getLensType())) {
            out.add("");
            out.add("# Prescription");
            if (order.getRxJson() != null && !order.getRxJson().isBlank()) out.add("Rx: " + order.getRxJson());
            out.add(order.getPrescriptionFileId() != null
                    ? "Customer uploaded their prescription — sent with this alert, also in admin."
                    : "⚠ NO prescription on file — WhatsApp the customer before cutting lenses.");
        }
        out.add("");
        out.add("Pack & dispatch: " + adminLink(order));
        return out;
    }

    private String shopName(java.util.UUID storeId) {
        return stores.findById(storeId).map(com.specskart.pos.Store::getName).orElse("(removed shop)");
    }

    private String adminLink(Order order) {
        return props.frontendBaseUrl() + "/admin/orders/" + order.getId();
    }

    private static String label(String code) {
        String s = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /** Short, single-line status headline — fills {{2}} of the order-update template. Null = no customer message. */
    public static String statusLine(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "Order confirmed — pay cash when it arrives";
            case PAID -> "Payment received — we're preparing your frames";
            case ACCEPTED -> "Your shop has your order — we're getting your frames ready";
            case PACKED -> "Packed and ready for dispatch";
            case SHIPPED -> "Out for delivery — on its way to you";
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
        recordNote(order, what + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }

    private void recordNote(Order order, String text) {
        try {
            String note = "⚠ " + text;
            OrderEvent ev = new OrderEvent();
            ev.setOrderId(order.getId());
            ev.setStatus(order.getStatus());
            ev.setNote(note.length() > 500 ? note.substring(0, 500) : note);
            orderEvents.save(ev);
        } catch (Exception ex) {
            log.warn("could not record notification failure for order {}: {}", order.getOrderNo(), ex.getMessage());
        }
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
