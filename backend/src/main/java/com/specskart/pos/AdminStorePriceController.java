package com.specskart.pos;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Per-store price override — {@link InventoryService#listForStore} already resolves and
 *  shows the effective price (override or the product's own default); this is where staff
 *  set or clear one. */
@RestController
@RequestMapping("/api/admin/pos/stores/{storeId}/prices")
public class AdminStorePriceController {

    public record SetPrice(long priceMinor) {}

    private final ProductStorePriceRepository prices;

    public AdminStorePriceController(ProductStorePriceRepository prices) {
        this.prices = prices;
    }

    @PutMapping("/{productId}")
    public void set(@PathVariable UUID storeId, @PathVariable UUID productId, @RequestBody SetPrice req) {
        ProductStorePrice p = prices.findByStoreIdAndProductId(storeId, productId).orElseGet(() -> {
            ProductStorePrice fresh = new ProductStorePrice();
            fresh.setStoreId(storeId);
            fresh.setProductId(productId);
            return fresh;
        });
        p.setPriceMinor(req.priceMinor());
        prices.save(p);
    }

    /** Reverts to the product's own default price for this store. */
    @DeleteMapping("/{productId}")
    public void clear(@PathVariable UUID storeId, @PathVariable UUID productId) {
        prices.findByStoreIdAndProductId(storeId, productId).ifPresent(prices::delete);
    }
}
