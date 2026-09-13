package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
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
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminStorePriceController(ProductStorePriceRepository prices, CurrentUser currentUser, AuditLogService audit) {
        this.prices = prices;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PutMapping("/{productId}")
    public void set(@PathVariable UUID storeId, @PathVariable UUID productId, @RequestBody SetPrice req, Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        ProductStorePrice p = prices.findByStoreIdAndProductId(storeId, productId).orElseGet(() -> {
            ProductStorePrice fresh = new ProductStorePrice();
            fresh.setStoreId(storeId);
            fresh.setProductId(productId);
            return fresh;
        });
        p.setPriceMinor(req.priceMinor());
        prices.save(p);
        audit.record("PRICE", productId.toString(), "SET", currentUser.idOf(auth), currentUser.nameOf(auth),
                storeId, "Set store price to " + req.priceMinor());
    }

    /** Reverts to the product's own default price for this store. */
    @DeleteMapping("/{productId}")
    public void clear(@PathVariable UUID storeId, @PathVariable UUID productId, Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        prices.findByStoreIdAndProductId(storeId, productId).ifPresent(prices::delete);
        audit.record("PRICE", productId.toString(), "CLEAR", currentUser.idOf(auth), currentUser.nameOf(auth),
                storeId, "Reset to default price");
    }
}
