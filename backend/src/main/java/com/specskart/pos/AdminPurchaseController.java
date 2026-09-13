package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/invoices")
public class AdminPurchaseController {

    private final PurchaseService purchases;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminPurchaseController(PurchaseService purchases, CurrentUser currentUser, AuditLogService audit) {
        this.purchases = purchases;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping
    public PosDtos.InvoiceView post(@RequestBody PosDtos.PostInvoice req, Authentication auth) {
        currentUser.assertStoreAccess(auth, req.storeId());
        PosDtos.InvoiceView invoice = purchases.postInvoice(req, currentUser.idOf(auth), currentUser.nameOf(auth));
        audit.record("PURCHASE", invoice.id().toString(), "CREATE", currentUser.idOf(auth), currentUser.nameOf(auth),
                invoice.storeId(), invoice.invoiceNumber() + " · " + invoice.totalMinor());
        return invoice;
    }

    @GetMapping("/{id}")
    public PosDtos.InvoiceView get(@PathVariable UUID id, Authentication auth) {
        PosDtos.InvoiceView invoice = purchases.getInvoice(id);
        currentUser.assertStoreAccess(auth, invoice.storeId());
        return invoice;
    }

    @PostMapping("/{id}/payments")
    public PosDtos.InvoiceView pay(@PathVariable UUID id, @RequestBody PosDtos.RecordPayment req, Authentication auth) {
        currentUser.assertStoreAccess(auth, purchases.getInvoice(id).storeId());
        PosDtos.InvoiceView invoice = purchases.recordPayment(id, req, currentUser.idOf(auth), currentUser.nameOf(auth));
        audit.record("PURCHASE", id.toString(), "PAYMENT", currentUser.idOf(auth), currentUser.nameOf(auth),
                invoice.storeId(), "Paid " + req.amountMinor() + " via " + req.method());
        return invoice;
    }

    /** {@code unpaidOnly} spans every shop -- an admin-only view (a shop's own login only
     *  ever needs its own unpaid invoices, already covered by {@code storeId}). */
    @GetMapping
    public List<PosDtos.InvoiceView> list(@RequestParam(required = false) UUID storeId,
                                          @RequestParam(defaultValue = "false") boolean unpaidOnly,
                                          Authentication auth) {
        if (unpaidOnly) {
            if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
            return purchases.unpaidInvoices();
        }
        if (storeId == null) return List.of();
        currentUser.assertStoreAccess(auth, storeId);
        return purchases.invoicesForStore(storeId);
    }
}
