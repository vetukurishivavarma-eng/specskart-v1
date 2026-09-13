package com.specskart.pos;

import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Posting a supplier invoice is what puts stock on the shelf: the goods, the paperwork
 *  reference, and the money owed are the same event — recording them separately is how a
 *  shop ends up with stock it can't account for. */
@Service
public class PurchaseService {

    private final SupplierRepository suppliers;
    private final SupplierInvoiceRepository invoices;
    private final SupplierInvoiceItemRepository invoiceItems;
    private final SupplierPaymentRepository payments;
    private final InventoryService inventory;
    private final StoreRepository stores;

    public PurchaseService(SupplierRepository suppliers, SupplierInvoiceRepository invoices,
                           SupplierInvoiceItemRepository invoiceItems, SupplierPaymentRepository payments,
                           InventoryService inventory, StoreRepository stores) {
        this.suppliers = suppliers;
        this.invoices = invoices;
        this.invoiceItems = invoiceItems;
        this.payments = payments;
        this.inventory = inventory;
        this.stores = stores;
    }

    @Transactional
    public PosDtos.SupplierView createSupplier(PosDtos.SupplierUpsert req) {
        if (req.name() == null || req.name().isBlank()) {
            throw ApiException.badRequest("NAME_REQUIRED", "Supplier name is required.");
        }
        Supplier s = new Supplier();
        apply(s, req);
        return view(suppliers.save(s));
    }

    @Transactional
    public PosDtos.SupplierView updateSupplier(UUID id, PosDtos.SupplierUpsert req) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "No such supplier."));
        apply(s, req);
        return view(suppliers.save(s));
    }

    private void apply(Supplier s, PosDtos.SupplierUpsert req) {
        if (req.name() != null) s.setName(req.name().trim());
        if (req.contactName() != null) s.setContactName(req.contactName());
        if (req.phone() != null) s.setPhone(req.phone());
        if (req.email() != null) s.setEmail(req.email());
        if (req.address() != null) s.setAddress(req.address());
        if (req.notes() != null) s.setNotes(req.notes());
    }

    @Transactional(readOnly = true)
    public List<PosDtos.SupplierView> listSuppliers() {
        return suppliers.findAllByOrderByNameAsc().stream().map(PurchaseService::view).toList();
    }

    /** Posts a delivery: creates the invoice + line items, puts every line's quantity on the
     *  shelf (stock_movements PURCHASE), and records the total owed. Entering the same paper
     *  invoice twice is refused — it would double the stock and double the debt. */
    @Transactional
    public PosDtos.InvoiceView postInvoice(PosDtos.PostInvoice req, UUID postedById, String postedByName) {
        if (req.items() == null || req.items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_INVOICE", "An invoice needs at least one item.");
        }
        Supplier supplier = suppliers.findById(req.supplierId())
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "No such supplier."));
        if (!stores.existsById(req.storeId())) {
            throw ApiException.notFound("STORE_NOT_FOUND", "No such store.");
        }
        if (invoices.existsBySupplierIdAndInvoiceNumber(supplier.getId(), req.invoiceNumber())) {
            throw ApiException.conflict("DUPLICATE_INVOICE", "This invoice number is already recorded for this supplier.");
        }

        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setSupplierId(supplier.getId());
        invoice.setStoreId(req.storeId());
        invoice.setInvoiceNumber(req.invoiceNumber());
        invoice.setInvoiceDate(req.invoiceDate());
        invoice.setDueDate(req.dueDate());
        invoice.setOtherChargesMinor(req.otherChargesMinor());
        invoice.setNotes(req.notes() == null ? "" : req.notes());
        invoice.setCreatedById(postedById);
        invoice.setCreatedByName(postedByName == null ? "" : postedByName);
        invoices.save(invoice);

        long subtotal = 0;
        for (PosDtos.InvoiceItemRequest line : req.items()) {
            if (line.quantity() <= 0) throw ApiException.badRequest("BAD_QTY", "Quantity must be positive.");
            long lineTotal = line.unitCostMinor() * line.quantity();
            subtotal += lineTotal;

            SupplierInvoiceItem item = new SupplierInvoiceItem();
            item.setInvoiceId(invoice.getId());
            item.setProductId(line.productId());
            item.setProductName(line.productName());
            item.setQuantity(line.quantity());
            item.setUnitCostMinor(line.unitCostMinor());
            item.setLineTotalMinor(lineTotal);
            invoiceItems.save(item);

            if (line.productId() != null) {
                inventory.adjust(req.storeId(), line.productId(), line.quantity(), "PURCHASE",
                        req.invoiceNumber(), "Received from " + supplier.getName(), postedById);
            }
        }

        invoice.setSubtotalMinor(subtotal);
        invoice.setTotalMinor(subtotal + req.otherChargesMinor());
        return view(invoices.save(invoice), supplier.getName());
    }

    /** A part-payment is the normal case — stock is often taken on credit and settled over
     *  weeks. Refuses a payment that would take the invoice past its own total. */
    @Transactional
    public PosDtos.InvoiceView recordPayment(UUID invoiceId, PosDtos.RecordPayment req, UUID userId, String userName) {
        SupplierInvoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "No such invoice."));
        if (req.amountMinor() <= 0) throw ApiException.badRequest("BAD_AMOUNT", "Payment amount must be positive.");
        if (invoice.getAmountPaidMinor() + req.amountMinor() > invoice.getTotalMinor()) {
            throw ApiException.badRequest("OVERPAYMENT", "That payment would exceed what's owed on this invoice.");
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setInvoiceId(invoiceId);
        payment.setAmountMinor(req.amountMinor());
        payment.setMethod(req.method() == null ? "CASH" : req.method());
        payment.setReference(req.reference() == null ? "" : req.reference());
        payment.setNote(req.note() == null ? "" : req.note());
        payment.setPaidAt(Instant.now());
        payment.setUserId(userId);
        payment.setUserName(userName == null ? "" : userName);
        payments.save(payment);

        invoice.setAmountPaidMinor(invoice.getAmountPaidMinor() + req.amountMinor());
        invoice.setStatus(invoice.getAmountPaidMinor() >= invoice.getTotalMinor() ? "PAID"
                : invoice.getAmountPaidMinor() > 0 ? "PARTIAL" : "UNPAID");
        Supplier supplier = suppliers.findById(invoice.getSupplierId()).orElse(null);
        return view(invoices.save(invoice), supplier == null ? "" : supplier.getName());
    }

    @Transactional(readOnly = true)
    public PosDtos.InvoiceView getInvoice(UUID id) {
        SupplierInvoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "No such invoice."));
        Supplier supplier = suppliers.findById(invoice.getSupplierId()).orElse(null);
        return view(invoice, supplier == null ? "" : supplier.getName());
    }

    @Transactional(readOnly = true)
    public List<PosDtos.InvoiceView> invoicesForStore(UUID storeId) {
        return invoices.findByStoreIdOrderByInvoiceDateDesc(storeId).stream()
                .map(inv -> view(inv, suppliers.findById(inv.getSupplierId()).map(Supplier::getName).orElse("")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PosDtos.InvoiceView> unpaidInvoices() {
        return invoices.findByStatusInOrderByInvoiceDateAsc(List.of("UNPAID", "PARTIAL")).stream()
                .map(inv -> view(inv, suppliers.findById(inv.getSupplierId()).map(Supplier::getName).orElse("")))
                .toList();
    }

    private static PosDtos.SupplierView view(Supplier s) {
        return new PosDtos.SupplierView(s.getId(), s.getName(), s.getContactName(), s.getPhone(),
                s.getEmail(), s.getAddress(), s.getNotes(), s.isActive());
    }

    private PosDtos.InvoiceView view(SupplierInvoice invoice, String supplierName) {
        var items = invoiceItems.findByInvoiceId(invoice.getId()).stream()
                .map(i -> new PosDtos.InvoiceItemView(i.getProductId(), i.getProductName(), i.getSku(),
                        i.getQuantity(), i.getUnitCostMinor(), i.getLineTotalMinor()))
                .toList();
        var pays = payments.findByInvoiceId(invoice.getId()).stream()
                .map(p -> new PosDtos.InvoicePaymentView(p.getAmountMinor(), p.getMethod(), p.getReference(), p.getPaidAt()))
                .toList();
        return new PosDtos.InvoiceView(invoice.getId(), invoice.getSupplierId(), supplierName, invoice.getStoreId(),
                invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getDueDate(),
                invoice.getSubtotalMinor(), invoice.getOtherChargesMinor(), invoice.getTotalMinor(),
                invoice.getAmountPaidMinor(), invoice.balanceMinor(), invoice.getStatus(), items, pays);
    }
}
