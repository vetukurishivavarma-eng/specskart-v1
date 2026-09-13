package com.specskart.pos;

import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/reorder")
public class AdminReorderController {

    private final InventoryService inventory;
    private final CurrentUser currentUser;

    public AdminReorderController(InventoryService inventory, CurrentUser currentUser) {
        this.inventory = inventory;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<InventoryService.ReorderLine> list(@RequestParam UUID storeId, Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        return inventory.reorderSuggestions(storeId);
    }
}
