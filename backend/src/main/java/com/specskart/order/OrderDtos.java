package com.specskart.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {}

    // ---- cart ----
    public record CartLine(UUID productId, String slug, String name, String imageUrl,
                           int qty, long unitPriceMinor, long lineTotalMinor, boolean inStock, int stockQty,
                           java.time.Instant heldUntil) {}

    public record CartView(String token, List<CartLine> lines, String promoCode,
                           long subtotalMinor, long discountMinor, long shippingMinor, long totalMinor,
                           String currency, String deliveryEta, java.time.Instant holdExpiresAt) {}

    public record AddItem(UUID productId, Integer qty) {}
    public record SetQty(int qty) {}
    public record ApplyPromo(String code) {}

    // ---- checkout ----
    public record CheckoutRequest(String customerName, String customerPhone, String customerEmail,
                                  String shipAddress, String shipCity) {}

    public record CheckoutResult(String orderNo, String checkoutUrl, long totalMinor, String currency) {}

    // ---- order views ----
    public record OrderLine(String productName, String productSlug, int qty, long unitPriceMinor, long lineTotalMinor) {}

    public record OrderView(String orderNo, String status, String customerName, String customerPhone,
                            String customerEmail, String shipAddress, String shipCity,
                            long subtotalMinor, long discountMinor, long shippingMinor, long totalMinor,
                            String currency, String promoCode, Instant paidAt, Instant createdAt,
                            List<OrderLine> lines, List<StatusEvent> timeline) {}

    public record StatusEvent(String status, String note, Instant at) {}

    public record AdminOrderRow(UUID id, String orderNo, String status, String customerName,
                                long totalMinor, String currency, Instant createdAt, Instant paidAt) {}

    public record StatusUpdate(String status, String note) {}
}
