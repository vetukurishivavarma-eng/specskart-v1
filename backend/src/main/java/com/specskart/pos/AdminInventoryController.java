package com.specskart.pos;

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

    public AdminInventoryController(InventoryService inventory, CurrentUser currentUser) {
        this.inventory = inventory;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<PosDtos.InventoryRow> list(@PathVariable UUID storeId) {
        return inventory.listForStore(storeId);
    }

    /** Manual stock correction — e.g. a physical count found a different number than the
     *  system has. Not for sales/purchases/transfers, which each post their own movement. */
    @PostMapping("/{productId}/adjust")
    public void adjust(@PathVariable UUID storeId, @PathVariable UUID productId,
                       @RequestBody PosDtos.AdjustStock req, Authentication auth) {
        inventory.adjust(storeId, productId, req.delta(), "ADJUSTMENT", null, req.note(), currentUser.idOf(auth));
    }
}
