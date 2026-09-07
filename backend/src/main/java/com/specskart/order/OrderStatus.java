package com.specskart.order;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    private static final java.util.Map<OrderStatus, Set<OrderStatus>> NEXT = java.util.Map.of(
            PENDING_PAYMENT, Set.of(PAID, CANCELLED),
            PAID, Set.of(PACKED, CANCELLED, REFUNDED),
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
