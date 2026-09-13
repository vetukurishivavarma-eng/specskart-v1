package com.specskart.pos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PosDtos {
    private PosDtos() {}

    public record StoreView(UUID id, String name, String code, String city, boolean active) {}
    public record CreateStore(String name, String code, String city) {}

    public record InventoryRow(UUID productId, String productName, String sku, int quantity,
                               int reorderLevel, long priceMinor) {}
    public record AdjustStock(int delta, String note) {}

    public record SaleItemRequest(UUID productId, int quantity, Long unitPriceMinor, Long discountMinor) {}
    public record PaymentRequest(String method, long amountMinor, String reference) {}
    public record CreateSale(UUID storeId, List<SaleItemRequest> items, List<PaymentRequest> payments,
                             String customerName, String customerPhone, String notes, String clientReference) {}

    public record SaleItemView(UUID productId, String productName, String sku, int quantity,
                               long unitPriceMinor, long lineTotalMinor) {}
    public record PaymentView(String method, long amountMinor, String reference) {}
    public record SaleView(UUID id, String receiptNumber, UUID storeId, String status,
                           long subtotalMinor, long discountMinor, long totalMinor,
                           String cashierName, String customerName, String customerPhone,
                           List<SaleItemView> items, List<PaymentView> payments, Instant createdAt) {}

    public record VoidSale(String reason) {}
}
