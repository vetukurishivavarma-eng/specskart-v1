package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Per-store stock and price — the online store keeps using {@link Product}'s own
 *  stockQty/priceMinor as its defaults; a store here overrides either. */
@Service
public class InventoryService {

    private final ProductInventoryRepository inventory;
    private final ProductStorePriceRepository prices;
    private final StockMovementRepository movements;
    private final ProductRepository products;

    public InventoryService(ProductInventoryRepository inventory, ProductStorePriceRepository prices,
                            StockMovementRepository movements, ProductRepository products) {
        this.inventory = inventory;
        this.prices = prices;
        this.movements = movements;
        this.products = products;
    }

    public int quantityOf(UUID storeId, UUID productId) {
        return inventory.findByStoreIdAndProductId(storeId, productId).map(ProductInventory::getQuantity).orElse(0);
    }

    public long priceOf(UUID storeId, UUID productId, long defaultPriceMinor) {
        return prices.findByStoreIdAndProductId(storeId, productId)
                .map(ProductStorePrice::getPriceMinor).orElse(defaultPriceMinor);
    }

    @Transactional(readOnly = true)
    public List<PosDtos.InventoryRow> listForStore(UUID storeId) {
        return products.findAll().stream()
                .map(p -> new PosDtos.InventoryRow(p.getId(), p.getName(), p.getSku() == null ? "" : p.getSku(),
                        quantityOf(storeId, p.getId()),
                        inventory.findByStoreIdAndProductId(storeId, p.getId()).map(ProductInventory::getReorderLevel).orElse(3),
                        priceOf(storeId, p.getId(), p.getPriceMinor())))
                .toList();
    }

    /** Applies a signed stock change and records why. Allows going negative on a manual
     *  ADJUSTMENT (correcting a miscount); {@link #decrementForSale} refuses to oversell. */
    @Transactional
    public void adjust(UUID storeId, UUID productId, int delta, String movementType, String reference,
                       String note, UUID userId) {
        ProductInventory inv = inventory.findByStoreIdAndProductId(storeId, productId).orElseGet(() -> {
            ProductInventory fresh = new ProductInventory();
            fresh.setStoreId(storeId);
            fresh.setProductId(productId);
            return fresh;
        });
        inv.setQuantity(inv.getQuantity() + delta);
        inventory.save(inv);

        StockMovement m = new StockMovement();
        m.setStoreId(storeId);
        m.setProductId(productId);
        m.setMovementType(movementType);
        m.setQuantity(delta);
        m.setBalance(inv.getQuantity());
        m.setReference(reference);
        m.setNote(note);
        m.setUserId(userId);
        movements.save(m);
    }

    /** Throws INSUFFICIENT_STOCK rather than allowing an in-person sale to oversell — unlike
     *  NG POS's offline tills, this app is online-only, so there's no queued-replay reason to
     *  let stock go negative here. */
    @Transactional
    public void decrementForSale(UUID storeId, UUID productId, int quantity, String saleReceiptNumber, UUID userId) {
        if (quantityOf(storeId, productId) < quantity) {
            Product p = products.findById(productId).orElse(null);
            throw ApiException.conflict("INSUFFICIENT_STOCK",
                    "Not enough stock of " + (p == null ? "that item" : p.getName()) + " at this store.");
        }
        adjust(storeId, productId, -quantity, "SALE", saleReceiptNumber, null, userId);
    }
}
