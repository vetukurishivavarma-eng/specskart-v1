package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/stores/{storeId}/inventory")
public class AdminInventoryController {

    private final InventoryService inventory;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminInventoryController(InventoryService inventory, CurrentUser currentUser, AuditLogService audit) {
        this.inventory = inventory;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping
    public List<PosDtos.InventoryRow> list(@PathVariable UUID storeId, Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        return inventory.listForStore(storeId);
    }

    /** Manual stock correction — e.g. a physical count found a different number than the
     *  system has. Not for sales/purchases/transfers, which each post their own movement. */
    @PostMapping("/{productId}/adjust")
    public void adjust(@PathVariable UUID storeId, @PathVariable UUID productId,
                       @RequestBody PosDtos.AdjustStock req, Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        inventory.adjust(storeId, productId, req.delta(), "ADJUSTMENT", null, req.note(), currentUser.idOf(auth));
        audit.record("STOCK", productId.toString(), "ADJUST", currentUser.idOf(auth), currentUser.nameOf(auth),
                storeId, "Adjusted stock by " + req.delta() + (req.note() == null || req.note().isBlank() ? "" : " (" + req.note() + ")"));
    }

    /** Bulk stock upload — one row per product (by SKU), each setting the absolute quantity
     *  from a fresh physical count. Returns any SKUs that didn't match a product. */
    @PostMapping("/import")
    public List<String> bulkImport(@PathVariable UUID storeId, @RequestBody List<InventoryService.SkuQuantity> lines,
                                   Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        List<String> skipped = inventory.bulkSetBySku(storeId, lines, currentUser.idOf(auth));
        audit.record("STOCK", null, "BULK_IMPORT", currentUser.idOf(auth), currentUser.nameOf(auth),
                storeId, (lines.size() - skipped.size()) + " SKU(s) updated, " + skipped.size() + " skipped");
        return skipped;
    }
}
