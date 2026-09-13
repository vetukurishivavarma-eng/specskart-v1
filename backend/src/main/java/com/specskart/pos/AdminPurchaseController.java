package com.specskart.pos;

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

    public AdminPurchaseController(PurchaseService purchases, CurrentUser currentUser) {
        this.purchases = purchases;
        this.currentUser = currentUser;
    }

    @PostMapping
    public PosDtos.InvoiceView post(@RequestBody PosDtos.PostInvoice req, Authentication auth) {
        return purchases.postInvoice(req, currentUser.idOf(auth), currentUser.nameOf(auth));
    }

    @GetMapping("/{id}")
    public PosDtos.InvoiceView get(@PathVariable UUID id) {
        return purchases.getInvoice(id);
    }

    @PostMapping("/{id}/payments")
    public PosDtos.InvoiceView pay(@PathVariable UUID id, @RequestBody PosDtos.RecordPayment req, Authentication auth) {
        return purchases.recordPayment(id, req, currentUser.idOf(auth), currentUser.nameOf(auth));
    }

    @GetMapping
    public List<PosDtos.InvoiceView> list(@RequestParam(required = false) UUID storeId,
                                          @RequestParam(defaultValue = "false") boolean unpaidOnly) {
        if (unpaidOnly) return purchases.unpaidInvoices();
        if (storeId == null) return List.of();
        return purchases.invoicesForStore(storeId);
    }
}
