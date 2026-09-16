package com.specskart.order;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    /** Cash-on-delivery order, confirmed and ready to fulfil — payment collected on hand-over. */
    CONFIRMED,
    PAID,
    /** The fulfilling shop has the order on its bench — the first thing staff tap in the POS app. */
    ACCEPTED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    // ACCEPTED is optional everywhere it appears: a shop that packs straight away can still go
    // PAID -> PACKED, so orders placed before the POS delivery screen existed keep moving.
    private static final java.util.Map<OrderStatus, Set<OrderStatus>> NEXT = java.util.Map.of(
            PENDING_PAYMENT, Set.of(PAID, CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PAID, ACCEPTED, PACKED, CANCELLED),
            PAID, Set.of(ACCEPTED, PACKED, CANCELLED, REFUNDED),
            ACCEPTED, Set.of(PACKED, CANCELLED, REFUNDED),
            PACKED, Set.of(SHIPPED, CANCELLED, REFUNDED),
            SHIPPED, Set.of(DELIVERED, REFUNDED),
            DELIVERED, Set.of(REFUNDED),
            CANCELLED, Set.of(),
            REFUNDED, Set.of());

    public boolean canMoveTo(OrderStatus target) {
        return NEXT.getOrDefault(this, Set.of()).contains(target);
    }

    /** Whether reaching this status should release the stock reserved at checkout. */
    public boolean releasesStock() {
        return this == CANCELLED;
    }
}
