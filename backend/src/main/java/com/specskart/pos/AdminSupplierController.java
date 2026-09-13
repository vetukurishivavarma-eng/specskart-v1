package com.specskart.pos;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/suppliers")
public class AdminSupplierController {

    private final PurchaseService purchases;

    public AdminSupplierController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    public List<PosDtos.SupplierView> list() {
        return purchases.listSuppliers();
    }

    @PostMapping
    public PosDtos.SupplierView create(@RequestBody PosDtos.SupplierUpsert req) {
        return purchases.createSupplier(req);
    }

    @PutMapping("/{id}")
    public PosDtos.SupplierView update(@PathVariable UUID id, @RequestBody PosDtos.SupplierUpsert req) {
        return purchases.updateSupplier(id, req);
    }
}
