package com.specskart.catalog;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private final AdminCatalogService admin;

    public AdminCatalogController(AdminCatalogService admin) {
        this.admin = admin;
    }

    @GetMapping("/products")
    public List<CatalogDtos.AdminProduct> products() {
        return admin.listProducts();
    }

    @GetMapping("/products/{id}")
    public CatalogDtos.AdminProduct product(@PathVariable UUID id) {
        return admin.getProduct(id);
    }

    @PostMapping("/products")
    public CatalogDtos.AdminProduct create(@RequestBody CatalogDtos.ProductUpsert body) {
        return admin.createProduct(body);
    }

    @PutMapping("/products/{id}")
    public CatalogDtos.AdminProduct update(@PathVariable UUID id, @RequestBody CatalogDtos.ProductUpsert body) {
        return admin.updateProduct(id, body);
    }

    @DeleteMapping("/products/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        admin.deleteProduct(id);
        return Map.of("deleted", id);
    }

    @GetMapping("/promos")
    public List<CatalogDtos.AdminPromo> promos() {
        return admin.listPromos();
    }

    @PostMapping("/promos")
    public CatalogDtos.AdminPromo createPromo(@RequestBody CatalogDtos.PromoUpsert body) {
        return admin.upsertPromo(null, body);
    }

    @PutMapping("/promos/{id}")
    public CatalogDtos.AdminPromo updatePromo(@PathVariable UUID id, @RequestBody CatalogDtos.PromoUpsert body) {
        return admin.upsertPromo(id, body);
    }

    @DeleteMapping("/promos/{id}")
    public Map<String, Object> deletePromo(@PathVariable UUID id) {
        admin.deletePromo(id);
        return Map.of("deleted", id);
    }

    @PutMapping("/store-config")
    public CatalogDtos.StoreConfigDto storeConfig(@RequestBody CatalogDtos.StoreConfigUpsert body) {
        return admin.updateStoreConfig(body);
    }
}
