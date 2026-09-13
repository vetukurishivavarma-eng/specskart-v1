package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Rings up a till sale: resolves per-store pricing, checks stock, decrements it, and posts
 *  the sale + its line items + payments in one transaction. {@code clientReference} makes a
 *  retried request a no-op instead of a duplicate charge. */
@Service
public class SaleService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final PosSaleRepository sales;
    private final PosSaleItemRepository saleItems;
    private final PosPaymentRepository payments;
    private final ReceiptCounterRepository counters;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final InventoryService inventory;

    public SaleService(PosSaleRepository sales, PosSaleItemRepository saleItems, PosPaymentRepository payments,
                       ReceiptCounterRepository counters, StoreRepository stores, ProductRepository products,
                       InventoryService inventory) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.payments = payments;
        this.counters = counters;
        this.stores = stores;
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional
    public PosDtos.SaleView createSale(PosDtos.CreateSale req, UUID cashierId, String cashierName) {
        if (req.clientReference() != null && !req.clientReference().isBlank()) {
            var existing = sales.findByClientReference(req.clientReference());
            if (existing.isPresent()) return view(existing.get());
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_SALE", "A sale needs at least one item.");
        }
        Store store = stores.findById(req.storeId())
                .orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store."));

        PosSale sale = new PosSale();
        sale.setStoreId(store.getId());
        sale.setReceiptNumber(nextReceiptNumber(store));
        sale.setCashierId(cashierId);
        sale.setCashierName(cashierName == null ? "" : cashierName);
        sale.setCustomerName(req.customerName());
        sale.setCustomerPhone(req.customerPhone());
        sale.setNotes(req.notes() == null ? "" : req.notes());
        sale.setClientReference(req.clientReference());
        sales.save(sale);

        long subtotal = 0;
        for (PosDtos.SaleItemRequest line : req.items()) {
            if (line.quantity() <= 0) throw ApiException.badRequest("BAD_QTY", "Quantity must be positive.");
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No such product."));
            long unitPrice = line.unitPriceMinor() != null ? line.unitPriceMinor()
                    : inventory.priceOf(store.getId(), product.getId(), product.getPriceMinor());
            long discount = line.discountMinor() == null ? 0 : line.discountMinor();
            long lineTotal = unitPrice * line.quantity() - discount;
            subtotal += lineTotal;

            inventory.decrementForSale(store.getId(), product.getId(), line.quantity(), sale.getReceiptNumber(), cashierId);

            PosSaleItem item = new PosSaleItem();
            item.setSaleId(sale.getId());
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setSku(product.getSku() == null ? "" : product.getSku());
            item.setQuantity(line.quantity());
            item.setUnitPriceMinor(unitPrice);
            item.setDiscountMinor(discount);
            item.setLineTotalMinor(lineTotal);
            saleItems.save(item);
        }

        long paid = 0;
        List<PosDtos.PaymentRequest> paymentReqs = req.payments() == null ? List.of() : req.payments();
        for (PosDtos.PaymentRequest p : paymentReqs) {
            PosPayment payment = new PosPayment();
            payment.setSaleId(sale.getId());
            payment.setMethod(p.method());
            payment.setAmountMinor(p.amountMinor());
            payment.setReference(p.reference());
            payments.save(payment);
            paid += p.amountMinor();
        }
        if (paid < subtotal) {
            throw ApiException.badRequest("UNDERPAID", "Payments (" + paid + ") don't cover the sale total (" + subtotal + ").");
        }

        sale.setSubtotalMinor(subtotal);
        sale.setDiscountMinor(0);
        sale.setTotalMinor(subtotal);
        return view(sales.save(sale));
    }

    @Transactional
    public PosDtos.SaleView voidSale(UUID id, String reason, UUID voidedBy) {
        PosSale sale = sales.findById(id).orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale."));
        if (sale.isVoided()) return view(sale);
        for (PosSaleItem item : saleItems.findBySaleId(sale.getId())) {
            inventory.adjust(sale.getStoreId(), item.getProductId(), item.getQuantity(), "REFUND",
                    sale.getReceiptNumber(), "Voided: " + reason, voidedBy);
        }
        sale.setStatus("VOIDED");
        sale.setVoidedAt(Instant.now());
        sale.setVoidReason(reason);
        return view(sales.save(sale));
    }

    @Transactional(readOnly = true)
    public List<PosDtos.SaleView> salesForStore(UUID storeId, Instant from, Instant to) {
        return sales.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(storeId, from, to).stream()
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PosDtos.SaleView getSale(UUID id) {
        return view(sales.findById(id).orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale.")));
    }

    /** A partial or full refund against a specific sale — distinct from {@link #voidSale},
     *  which cancels the whole thing. Posts as its own reversing sale (sale_type REFUND,
     *  negative line totals and payment) rather than mutating the original, so the original
     *  receipt stays exactly as it was printed. ponytail: doesn't track how much of each line
     *  has already been refunded across multiple partial refunds — just checks against the
     *  original quantity sold, so refunding the same line twice would go unnoticed. Fine for
     *  a small shop's volume; add per-line refunded-so-far tracking if that becomes real. */
    @Transactional
    public PosDtos.SaleView refund(UUID originalSaleId, List<PosDtos.SaleItemRequest> items, String method,
                                   String reason, UUID userId) {
        PosSale original = sales.findById(originalSaleId)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale."));
        if (original.isVoided()) throw ApiException.badRequest("ALREADY_VOIDED", "This sale was voided, not sold.");
        if (items == null || items.isEmpty()) throw ApiException.badRequest("EMPTY_REFUND", "Pick at least one item to refund.");

        var originalItems = saleItems.findBySaleId(originalSaleId);

        PosSale refundSale = new PosSale();
        refundSale.setStoreId(original.getStoreId());
        refundSale.setSaleType("REFUND");
        refundSale.setReceiptNumber(nextReceiptNumber(stores.findById(original.getStoreId()).orElseThrow()));
        refundSale.setCashierId(userId);
        refundSale.setReversesId(originalSaleId);
        refundSale.setNotes(reason == null ? "" : reason);
        sales.save(refundSale);

        long refundTotal = 0;
        for (PosDtos.SaleItemRequest line : items) {
            PosSaleItem originalItem = originalItems.stream()
                    .filter(i -> i.getProductId() != null && i.getProductId().equals(line.productId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("NOT_ON_SALE", "That item wasn't on the original sale."));
            if (line.quantity() <= 0 || line.quantity() > originalItem.getQuantity()) {
                throw ApiException.badRequest("BAD_QTY", "Refund quantity must be between 1 and what was sold.");
            }
            long lineTotal = -(originalItem.getUnitPriceMinor() * line.quantity());
            refundTotal += lineTotal;

            inventory.adjust(original.getStoreId(), originalItem.getProductId(), line.quantity(), "REFUND",
                    refundSale.getReceiptNumber(), reason, userId);

            PosSaleItem refundItem = new PosSaleItem();
            refundItem.setSaleId(refundSale.getId());
            refundItem.setProductId(originalItem.getProductId());
            refundItem.setProductName(originalItem.getProductName());
            refundItem.setSku(originalItem.getSku());
            refundItem.setQuantity(line.quantity());
            refundItem.setUnitPriceMinor(originalItem.getUnitPriceMinor());
            refundItem.setLineTotalMinor(lineTotal);
            saleItems.save(refundItem);
        }

        PosPayment refundPayment = new PosPayment();
        refundPayment.setSaleId(refundSale.getId());
        refundPayment.setMethod(method == null ? "CASH" : method);
        refundPayment.setAmountMinor(refundTotal);
        payments.save(refundPayment);

        refundSale.setSubtotalMinor(refundTotal);
        refundSale.setTotalMinor(refundTotal);
        return view(sales.save(refundSale));
    }

    /** ponytail: increments within the enclosing transaction rather than SELECT ... FOR
     *  UPDATE — fine for one till posting at a time; the receipt_number unique constraint
     *  is the fail-safe if two ever collide. Upgrade to row-locking if concurrent registers
     *  become real. */
    private String nextReceiptNumber(Store store) {
        String day = DAY.format(Instant.now());
        ReceiptCounter counter = counters.findByStoreIdAndSaleDay(store.getId(), day).orElseGet(() -> {
            ReceiptCounter c = new ReceiptCounter();
            c.setStoreId(store.getId());
            c.setSaleDay(day);
            return c;
        });
        counter.setSequence(counter.getSequence() + 1);
        counters.save(counter);
        return "%s-%s-%06d".formatted(store.getCode(), day, counter.getSequence());
    }

    private PosDtos.SaleView view(PosSale sale) {
        var items = saleItems.findBySaleId(sale.getId()).stream()
                .map(i -> new PosDtos.SaleItemView(i.getProductId(), i.getProductName(), i.getSku(),
                        i.getQuantity(), i.getUnitPriceMinor(), i.getLineTotalMinor()))
                .toList();
        var pays = payments.findBySaleId(sale.getId()).stream()
                .map(p -> new PosDtos.PaymentView(p.getMethod(), p.getAmountMinor(), p.getReference()))
                .toList();
        return new PosDtos.SaleView(sale.getId(), sale.getReceiptNumber(), sale.getStoreId(), sale.getStatus(),
                sale.getSubtotalMinor(), sale.getDiscountMinor(), sale.getTotalMinor(),
                sale.getCashierName(), sale.getCustomerName(), sale.getCustomerPhone(),
                items, pays, sale.getCreatedAt());
    }
}
