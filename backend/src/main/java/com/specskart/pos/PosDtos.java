package com.specskart.pos;

import java.time.Instant;
import java.time.LocalDate;
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

    // ---- suppliers / purchase invoices ----

    public record SupplierView(UUID id, String name, String contactName, String phone, String email,
                               String address, String notes, boolean active) {}
    public record SupplierUpsert(String name, String contactName, String phone, String email,
                                 String address, String notes) {}

    public record InvoiceItemRequest(UUID productId, String productName, int quantity, long unitCostMinor) {}
    public record PostInvoice(UUID supplierId, UUID storeId, String invoiceNumber, LocalDate invoiceDate,
                              LocalDate dueDate, long otherChargesMinor, String notes,
                              List<InvoiceItemRequest> items) {}
    public record RecordPayment(long amountMinor, String method, String reference, String note) {}

    public record InvoiceItemView(UUID productId, String productName, String sku, int quantity,
                                  long unitCostMinor, long lineTotalMinor) {}
    public record InvoicePaymentView(long amountMinor, String method, String reference, Instant paidAt) {}
    public record InvoiceView(UUID id, UUID supplierId, String supplierName, UUID storeId, String invoiceNumber,
                              LocalDate invoiceDate, LocalDate dueDate, long subtotalMinor, long otherChargesMinor,
                              long totalMinor, long amountPaidMinor, long balanceMinor, String status,
                              List<InvoiceItemView> items, List<InvoicePaymentView> payments) {}

    // ---- transfers ----

    public record TransferItemRequest(UUID productId, int quantity) {}
    public record CreateTransfer(UUID fromStoreId, UUID toStoreId, String notes, List<TransferItemRequest> items) {}
    public record TransferItemView(UUID productId, String productName, int quantity) {}
    public record TransferView(UUID id, String reference, UUID fromStoreId, UUID toStoreId, String status,
                               String notes, List<TransferItemView> items, Instant createdAt) {}
}
