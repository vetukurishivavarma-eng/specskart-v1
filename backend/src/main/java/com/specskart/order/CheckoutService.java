package com.specskart.order;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import com.specskart.config.AppProperties;
import com.specskart.lead.LeadService;
import com.specskart.lead.LeadStatus;
import com.specskart.payment.PaymentProvider;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final String B32 = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no I,L,O,0,1

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderEventRepository orderEvents;
    private final ProductRepository products;
    private final PromoCodeRepository promos;
    private final PaymentProvider payments;
    private final CartService cartService;
    private final OrderNotificationService notifications;
    private final AnalyticsService analytics;
    private final LeadService leadService;
    private final LoyaltyService loyalty;
    private final AppProperties props;

    public CheckoutService(CartRepository carts, CartItemRepository cartItems, OrderRepository orders,
                           OrderItemRepository orderItems, OrderEventRepository orderEvents,
                           ProductRepository products, PromoCodeRepository promos, PaymentProvider payments,
                           CartService cartService, OrderNotificationService notifications,
                           AnalyticsService analytics, LeadService leadService, LoyaltyService loyalty,
                           AppProperties props) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.orders = orders;
        this.orderItems = orderItems;
        this.orderEvents = orderEvents;
        this.products = products;
        this.promos = promos;
        this.payments = payments;
        this.cartService = cartService;
        this.notifications = notifications;
        this.analytics = analytics;
        this.leadService = leadService;
        this.loyalty = loyalty;
        this.props = props;
    }

    @Transactional
    public OrderDtos.CheckoutResult start(String cartToken, OrderDtos.CheckoutRequest req) {
        require(req.customerName(), "a name");
        require(req.customerPhone(), "a phone number");
        require(req.shipAddress(), "a delivery address");
        require(req.shipCity(), "a city / town");

        Cart cart = carts.findByToken(cartToken)
                .orElseThrow(() -> ApiException.badRequest("CART_EMPTY", "Your bag is empty."));
        if (cart.getOrderedAt() != null) {
            throw ApiException.badRequest("CART_USED", "This bag has already been checked out.");
        }
        List<CartItem> lines = cartItems.findByCartId(cart.getId());
        if (lines.isEmpty()) throw ApiException.badRequest("CART_EMPTY", "Your bag is empty.");

        long subtotal = 0;
        var view = cartService.view(cart); // reuse pricing / promo logic
        Instant now = Instant.now();
        for (CartItem ci : lines) {
            Product p = products.findById(ci.getProductId())
                    .orElseThrow(() -> ApiException.badRequest("PRODUCT_GONE", "A frame in your bag is no longer available."));
            // Units were reserved when they went in the bag. If the hold lapsed (shopper idle
            // past the 15-min window), try to grab them again — someone else may have taken the
            // last one. Failure here rolls the whole checkout back.
            boolean held = ci.getHeldUntil() != null && ci.getHeldUntil().isAfter(now);
            if (!held && products.reserve(p.getId(), ci.getQty()) == 0) {
                throw ApiException.badRequest("OUT_OF_STOCK",
                        "\"" + p.getName() + "\" sold out while you were away — please adjust your bag.");
            }
            subtotal += ci.lineTotalMinor();
        }

        // Every order gets a lead so confirmation + status WhatsApps have a recipient and the
        // buyer shows in the CRM. A website buyer is matched/created by phone number.
        UUID leadId = cart.getLeadId();
        if (leadId == null) {
            var l = leadService.onWebOrder(req.customerPhone(), req.customerName());
            leadId = l != null ? l.getId() : null;
        }

        // referral discount + points redemption (both eat into subtotal-minus-promo; points
        // are decremented now and restored if the order is cancelled)
        long goods = subtotal + view.lensAddMinor();
        long afterPromo = Math.max(0, goods - view.discountMinor());
        LoyaltyService.CheckoutOutcome lo = leadId == null
                ? new LoyaltyService.CheckoutOutcome(0, 0, null, null)
                : loyalty.applyAtCheckout(leadId, afterPromo, req.redeemPoints(), req.referralCode());
        long discountMinor = view.discountMinor() + lo.discountMinor();
        long total = Math.max(0, goods - discountMinor) + view.shippingMinor();

        Order order = new Order();
        order.setOrderNo(freshOrderNo());
        order.setLeadId(leadId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCustomerName(req.customerName().trim());
        order.setCustomerPhone(req.customerPhone().trim());
        order.setCustomerEmail(req.customerEmail() == null ? null : req.customerEmail().trim());
        order.setShipAddress(req.shipAddress().trim());
        order.setShipCity(req.shipCity().trim());
        order.setSubtotalMinor(subtotal);
        order.setDiscountMinor(discountMinor);
        order.setShippingMinor(view.shippingMinor());
        order.setTotalMinor(total);
        order.setCurrency(view.currency());
        order.setPromoCode(view.promoCode());
        order.setPaymentProvider(payments.name());
        order.setReferredByLeadId(lo.referrerLeadId());
        order.setPointsRedeemed(lo.pointsRedeemed());
        order.setLensType(cart.getLensType());
        order.setLensAddMinor(view.lensAddMinor());
        order.setRxJson(cart.getRxJson());
        orders.save(order);

        for (CartItem ci : lines) {
            Product p = products.findById(ci.getProductId()).orElseThrow();
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setProductId(p.getId());
            oi.setProductName(p.getName());
            oi.setProductSlug(p.getSlug());
            oi.setQty(ci.getQty());
            oi.setUnitPriceMinor(ci.getUnitPriceMinor());
            orderItems.save(oi);
            // stock is already off the shelf (reserved at add-to-cart / re-grabbed above); the
            // reservation becomes permanent once the cart is marked ordered. Released on CANCELLED.
        }
        if (view.promoCode() != null) {
            promos.findByCodeIgnoreCase(view.promoCode()).ifPresent(pc -> {
                pc.setRedeemedCount(pc.getRedeemedCount() + 1);
                promos.save(pc);
            });
        }
        event(order, OrderStatus.PENDING_PAYMENT, "Order placed");

        cart.setOrderedAt(Instant.now());
        carts.save(cart);

        if (order.getLeadId() != null) {
            analytics.record(LeadEventType.ORDER_PLACED, order.getLeadId(), null);
            leadService.advanceStatusSoft(order.getLeadId(), LeadStatus.INTERESTED);
        }

        String redirect = props.frontendBaseUrl() + "/order/" + order.getOrderNo();
        var payment = payments.start(new PaymentProvider.PaymentRequest(
                order.getOrderNo(), order.getTotalMinor(), order.getCurrency(),
                order.getCustomerName(), order.getCustomerEmail(), order.getCustomerPhone(), redirect));
        order.setPaymentRef(payment.providerRef());
        orders.save(order);

        return new OrderDtos.CheckoutResult(order.getOrderNo(), payment.checkoutUrl(),
                order.getTotalMinor(), order.getCurrency());
    }

    /** Called from the payment webhook and the return page. Idempotent; always re-verifies with the gateway. */
    @Transactional
    public void confirmPayment(String providerRef) {
        Order order = orders.findByPaymentRef(providerRef)
                .or(() -> orders.findByOrderNo(providerRef))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No order for ref " + providerRef));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) return; // already handled

        if (!payments.verify(order.getPaymentRef() != null ? order.getPaymentRef() : order.getOrderNo())) {
            log.warn("payment {} for order {} did not verify", providerRef, order.getOrderNo());
            return;
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orders.save(order);
        event(order, OrderStatus.PAID, "Payment confirmed");

        if (order.getLeadId() != null) {
            analytics.record(LeadEventType.ORDER_PAID, order.getLeadId(), null);
            analytics.record(LeadEventType.LEAD_CONVERTED, order.getLeadId(), null);
            leadService.advanceStatusSoft(order.getLeadId(), LeadStatus.CONVERTED);
            loyalty.onOrderPaid(order);
            orders.save(order);
        }
        notifications.onStatus(order, OrderStatus.PAID);
    }

    @Transactional
    public Order updateStatus(UUID orderId, OrderStatus target, String note) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No such order."));
        if (!order.getStatus().canMoveTo(target)) {
            throw ApiException.badRequest("BAD_TRANSITION",
                    "Can't move " + order.getStatus() + " → " + target + ".");
        }
        if (target.releasesStock()) restock(order);
        if (target == OrderStatus.CANCELLED || target == OrderStatus.REFUNDED) loyalty.onOrderReversed(order);
        order.setStatus(target);
        orders.save(order);
        event(order, target, note);
        if (target == OrderStatus.DELIVERED && order.getLeadId() != null) {
            analytics.record(LeadEventType.ORDER_DELIVERED, order.getLeadId(), null);
        }
        notifications.onStatus(order, target);
        return order;
    }

    private void restock(Order order) {
        for (OrderItem oi : orderItems.findByOrderId(order.getId())) {
            if (oi.getProductId() != null) products.release(oi.getProductId(), oi.getQty());
        }
    }

    private void event(Order order, OrderStatus status, String note) {
        OrderEvent e = new OrderEvent();
        e.setOrderId(order.getId());
        e.setStatus(status);
        e.setNote(note);
        orderEvents.save(e);
    }

    // Unguessable: the tracking page (address + phone) is reachable by order number alone.
    private String freshOrderNo() {
        for (int i = 0; i < 12; i++) {
            StringBuilder sb = new StringBuilder("SK-");
            for (int j = 0; j < 8; j++) sb.append(B32.charAt(RNG.nextInt(B32.length())));
            String no = sb.toString();
            if (!orders.existsByOrderNo(no)) return no;
        }
        return "SK-" + System.currentTimeMillis();
    }

    private static void require(String v, String what) {
        if (v == null || v.isBlank()) {
            throw ApiException.badRequest("MISSING_FIELD", "Please enter " + what + ".");
        }
    }
}
