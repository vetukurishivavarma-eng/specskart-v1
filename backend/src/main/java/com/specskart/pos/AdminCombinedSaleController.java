package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Specskart POS: one bill for frame + lenses. See CombinedSaleService. */
@RestController
@RequestMapping("/api/admin/pos/combined-sales")
public class AdminCombinedSaleController {

    private final CombinedSaleService service;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminCombinedSaleController(CombinedSaleService service, CurrentUser currentUser, AuditLogService audit) {
        this.service = service;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping
    public CombinedSaleService.CombinedView create(@RequestBody CombinedSaleService.CombinedSale req, Authentication auth) {
        if (req.frame() != null) currentUser.assertStoreAccess(auth, req.frame().storeId());
        if (req.lens() != null && req.lens().storeId() != null) currentUser.assertStoreAccess(auth, req.lens().storeId());
        var bill = service.create(req, currentUser.idOf(auth), currentUser.nameOf(auth));
        if (bill.frame() != null) {
            audit.record("SALE", bill.frame().id().toString(), "CREATE", currentUser.idOf(auth), currentUser.nameOf(auth),
                    bill.frame().storeId(), bill.frame().receiptNumber() + " · " + bill.totalMinor() + (bill.lens() != null ? " (frame + lenses)" : ""));
        }
        return bill;
    }
}
