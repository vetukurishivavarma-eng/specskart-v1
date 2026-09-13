package com.specskart.pos;

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

    public AdminReorderController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<InventoryService.ReorderLine> list(@RequestParam UUID storeId) {
        return inventory.reorderSuggestions(storeId);
    }
}
