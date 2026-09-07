package com.specskart.catalog;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Storefront-facing catalog. No auth. */
@RestController
@RequestMapping("/api/public")
public class PublicCatalogController {

    private final CatalogService catalog;

    public PublicCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public List<CatalogDtos.ProductCard> browse(@RequestParam(required = false) String faceShape,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String gender,
                                                @RequestParam(required = false) String sort) {
        return catalog.browse(faceShape, category, gender, sort);
    }

    @GetMapping("/products/featured")
    public List<CatalogDtos.ProductCard> featured() {
        return catalog.featured();
    }

    @GetMapping("/products/{slug}")
    public CatalogDtos.ProductDetail detail(@PathVariable String slug) {
        return catalog.detail(slug);
    }

    @PostMapping("/products/{slug}/notify-me")
    public Map<String, Object> notifyMe(@PathVariable String slug, @RequestBody Map<String, String> body) {
        catalog.registerStockAlert(slug, body.get("whatsapp"));
        return Map.of("ok", true);
    }

    @GetMapping("/store-config")
    public CatalogDtos.StoreConfigDto storeConfig() {
        return catalog.storeConfig();
    }
}
