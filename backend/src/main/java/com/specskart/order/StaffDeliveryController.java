package com.specskart.order;

import com.specskart.pos.Store;
import com.specskart.pos.StoreRepository;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The POS app's Delivery screen for website orders: the queue of paid web orders this shop has to
 * fulfil, and the four taps that move one along — received by the shop, packed, out for delivery,
 * delivered. Each move goes through CheckoutService.updateStatus, so the customer's WhatsApp
 * update, the loyalty settlement and the order timeline all fire exactly as they do from admin.
 *
 * <p>Shop-scoped like every other POS endpoint: a scoped login only ever sees and touches orders
 * whose fulfilStoreId is its own shop (the shop the nearest-shop router picked at checkout).
 */
@RestController
@RequestMapping("/api/admin/deliveries")
public class StaffDeliveryController {

    /** Still on the shop's bench — the working queue. */
    private static final Set<OrderStatus> OPEN = EnumSet.of(
            OrderStatus.CONFIRMED, OrderStatus.PAID, OrderStatus.ACCEPTED,
            OrderStatus.PACKED, OrderStatus.SHIPPED);

    /** The only moves the Delivery screen offers. Cancelling or refunding stays in admin. */
    private static final Set<OrderStatus> ALLOWED_TARGETS = EnumSet.of(
            OrderStatus.ACCEPTED, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orders;
    private final StoreRepository stores;
    private final CheckoutService checkout;
    private final CurrentUser currentUser;

    public StaffDeliveryController(OrderRepository orders, StoreRepository stores,
                                   CheckoutService checkout, CurrentUser currentUser) {
        this.orders = orders;
        this.stores = stores;
        this.checkout = checkout;
        this.currentUser = currentUser;
    }

    /**
     * @param storeId the shop to list. Optional for an unscoped ADMIN (omit for every shop);
     *                a shop-scoped login always gets its own shop, whatever it asks for.
     * @param done    true to see what this shop has already delivered rather than the open queue.
     */
    @GetMapping
    public List<OrderDtos.DeliveryRow> list(Authentication auth,
                                            @RequestParam(name = "store_id", required = false) UUID storeId,
                                            @RequestParam(required = false, defaultValue = "false") boolean done) {
        UUID scoped = currentUser.storeIdOf(auth);
        UUID target = scoped != null ? scoped : storeId;
        if (target != null) currentUser.assertStoreAccess(auth, target);

        Set<OrderStatus> wanted = done ? EnumSet.of(OrderStatus.DELIVERED) : OPEN;
        List<Order> rows = target == null
                ? orders.findByStatusInOrderByCreatedAtDesc(wanted)
                : orders.findByFulfilStoreIdAndStatusInOrderByCreatedAtDesc(target, wanted);

        // A web order only reaches a shop once it is paid (or is a confirmed COD), so an unpaid
        // basket never shows up here even when the nearest-shop router already picked a shop.
        Map<UUID, String> names = storeNames(rows);
        List<OrderDtos.DeliveryRow> out = new ArrayList<>();
        for (Order o : rows) out.add(row(o, names));
        return out;
    }

    /** Move one order along. The transition itself is validated by OrderStatus.canMoveTo. */
    @PatchMapping("/{id}/status")
    public OrderDtos.DeliveryRow updateStatus(Authentication auth, @PathVariable UUID id,
                                              @RequestBody OrderDtos.StatusUpdate body) {
        Order order = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No such order."));
        // Scope on the order's own shop, not on a client-supplied id — an id in the URL must never
        // be a way around the shop boundary.
        currentUser.assertStoreAccess(auth, order.getFulfilStoreId());

        OrderStatus target;
        try {
            target = OrderStatus.valueOf(body.status());
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_STATUS", "Unknown order status: " + body.status());
        }
        if (!ALLOWED_TARGETS.contains(target)) {
            throw ApiException.badRequest("BAD_STATUS",
                    "The Delivery screen can only move an order to " + ALLOWED_TARGETS + ".");
        }
        Order saved = checkout.updateStatus(order.getId(), target, body.note());
        return row(saved, storeNames(List.of(saved)));
    }

    private Map<UUID, String> storeNames(List<Order> rows) {
        Set<UUID> ids = new java.util.HashSet<>();
        for (Order o : rows) if (o.getFulfilStoreId() != null) ids.add(o.getFulfilStoreId());
        Map<UUID, String> names = new java.util.HashMap<>();
        for (Store s : stores.findAllById(ids)) names.put(s.getId(), s.getName());
        return names;
    }

    private OrderDtos.DeliveryRow row(Order o, Map<UUID, String> storeNames) {
        return new OrderDtos.DeliveryRow(
                o.getId(), o.getOrderNo(), o.getStatus().name(), o.getCustomerName(), o.getCustomerPhone(),
                o.getDeliveryMethod(), o.getPickupPoint(), o.getShipAddress(), o.getShipCity(),
                o.getDeliveryLat(), o.getDeliveryLng(),
                lensOf(o), o.getPrescriptionFileId() != null, o.cashStillDue(),
                o.getTotalMinor(), o.getCurrency(),
                o.getFulfilStoreId(), storeNames.get(o.getFulfilStoreId()),
                o.getCreatedAt(), o.getPaidAt());
    }

    /** Lens orders need the lab before the box is packed, so the packer sees it on the row. */
    private String lensOf(Order o) {
        String lens = o.getLensType();
        return lens == null || lens.isBlank() ? null : lens;
    }
}
