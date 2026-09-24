package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-store stock and price. Every active shop with a map pin sells online: its shelf counts
 *  toward the website's stock ({@link Product#getStockQty()} = those shelves minus units held in
 *  shoppers' bags) and web orders ship from the nearest one holding the frames. While no shop has
 *  a pin, the online store keeps its own manually-edited stockQty. */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductInventoryRepository inventory;
    private final ProductStorePriceRepository prices;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final StoreRepository stores;

    public InventoryService(ProductInventoryRepository inventory, ProductStorePriceRepository prices,
                            StockMovementRepository movements, ProductRepository products,
                            StoreRepository stores) {
        this.inventory = inventory;
        this.prices = prices;
        this.movements = movements;
        this.products = products;
        this.stores = stores;
    }

    /** Shops whose stock sells online and that can ship web orders. */
    public List<Store> onlineShops() {
        return stores.findByActiveTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();
    }

    public boolean stockComesFromShops() {
        return !onlineShops().isEmpty();
    }

    private boolean isOnlineShop(UUID storeId) {
        return onlineShops().stream().anyMatch(s -> s.getId().equals(storeId));
    }

    /** A web order taking units off (negative) or putting them back on (positive) a shop's shelf.
     *  The website's own count was already moved by the cart hold, so this doesn't mirror back. */
    @Transactional
    public void adjustForWebOrder(UUID storeId, UUID productId, int delta, String movementType, String reference) {
        if (delta != 0) apply(storeId, productId, delta, movementType, reference, null, null, false);
    }

    public record Allocation(Map<UUID, Store> byProduct, Store primary, Double primaryDistanceKm, boolean split) {}

    /**
     * Which shop each web order line ships from: the nearest shop holding the whole order, else
     * line by line the nearest shop holding that line. Nearest = straight-line distance from the
     * customer's pin; without one, shops in the customer's city first, then the fullest shelves.
     * Null when no shop sells online.
     * ponytail: straight-line km, free and right while shops sit in different towns. The staff alert
     * carries a Google Maps directions link for the real road route. If two shops ever share a city
     * and road distance matters, a routing API (Google Routes / OpenRouteService) slots in here.
     */
    public Allocation allocate(Map<UUID, Integer> lines, Double lat, Double lng, String city) {
        List<Store> shops = onlineShops();
        if (shops.isEmpty() || lines.isEmpty()) return null;
        boolean pin = lat != null && lng != null;
        Comparator<Store> nearest = pin
                ? Comparator.comparingDouble((Store s) -> km(lat, lng, s.getLatitude(), s.getLongitude()))
                : Comparator.<Store>comparingInt(s -> sameCity(s, city) ? 0 : 1)
                        .thenComparingInt(s -> -lines.keySet().stream().mapToInt(p -> quantityOf(s.getId(), p)).sum());
        List<Store> ranked = shops.stream().sorted(nearest).toList();

        Store whole = ranked.stream()
                .filter(s -> lines.entrySet().stream().allMatch(e -> quantityOf(s.getId(), e.getKey()) >= e.getValue()))
                .findFirst().orElse(null);
        Map<UUID, Store> byProduct = new LinkedHashMap<>();
        for (var e : lines.entrySet()) {
            Store s = whole != null ? whole : ranked.stream()
                    .filter(x -> quantityOf(x.getId(), e.getKey()) >= e.getValue())
                    .findFirst()
                    // only reachable in a race (the website's count said it was there): take it from
                    // the fullest shelf and let that go negative so the POS shows the shortfall
                    .orElseGet(() -> ranked.stream()
                            .max(Comparator.comparingInt(x -> quantityOf(x.getId(), e.getKey()))).orElseThrow());
            byProduct.put(e.getKey(), s);
        }
        Store primary = ranked.stream().filter(byProduct.values()::contains).findFirst().orElseThrow();
        boolean split = byProduct.values().stream().map(Store::getId).distinct().count() > 1;
        return new Allocation(byProduct, primary,
                pin ? km(lat, lng, primary.getLatitude(), primary.getLongitude()) : null, split);
    }

    static double km(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static boolean sameCity(Store s, String city) {
        if (city == null || city.isBlank() || s.getCity() == null || s.getCity().isBlank()) return false;
        String a = city.trim().toLowerCase(), b = s.getCity().trim().toLowerCase();
        return a.contains(b) || b.contains(a);
    }

    /** Re-derive every product's online stock from the pinned shops' shelves. Safe any time — the
     *  invariant is simply shelves minus live bag holds — so it runs on every boot and whenever a
     *  shop starts or stops selling online. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resyncWebStock() {
        List<Store> shops = onlineShops();
        if (shops.isEmpty()) return;
        Instant now = Instant.now();
        int changed = 0;
        for (Product p : products.findAll()) {
            int shelves = shops.stream().mapToInt(s -> Math.max(0, quantityOf(s.getId(), p.getId()))).sum();
            int target = (int) Math.max(0, shelves - products.heldInOpenCarts(p.getId(), now));
            if (p.getStockQty() != target) {
                p.setStockQty(target);
                products.save(p);
                changed++;
            }
        }
        log.info("web stock resynced from {} online shop(s): {} product(s) corrected", shops.size(), changed);
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
                        priceOf(storeId, p.getId(), p.getPriceMinor()), p.getKind()))
                .toList();
    }

    /** Applies a signed stock change and records why. Allows going negative on a manual
     *  ADJUSTMENT (correcting a miscount); {@link #decrementForSale} refuses to oversell. */
    @Transactional
    public void adjust(UUID storeId, UUID productId, int delta, String movementType, String reference,
                       String note, UUID userId) {
        apply(storeId, productId, delta, movementType, reference, note, userId, true);
    }

    private void apply(UUID storeId, UUID productId, int delta, String movementType, String reference,
                       String note, UUID userId, boolean mirrorToWeb) {
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

        if (mirrorToWeb && isOnlineShop(storeId)) products.shiftStock(productId, delta);
    }

    /** Bulk stock upload — one line per product, by SKU, setting the ABSOLUTE quantity (a
     *  fresh physical count), not a delta. Lines with an unknown SKU are skipped and reported
     *  back rather than failing the whole upload. */
    @Transactional
    public List<String> bulkSetBySku(UUID storeId, List<SkuQuantity> lines, UUID userId) {
        List<String> skipped = new java.util.ArrayList<>();
        for (SkuQuantity line : lines) {
            Product p = products.findBySkuIgnoreCase(line.sku()).orElse(null);
            if (p == null) {
                skipped.add(line.sku());
                continue;
            }
            int delta = line.quantity() - quantityOf(storeId, p.getId());
            if (delta != 0) adjust(storeId, p.getId(), delta, "ADJUSTMENT", "bulk-import", "Bulk stock upload", userId);
        }
        return skipped;
    }

    public record SkuQuantity(String sku, int quantity) {}

    public record ReorderLine(UUID productId, String productName, String sku, int quantity,
                              int reorderLevel, int suggestedQuantity) {}

    /** ponytail: a simple heuristic — restock to double the reorder level, not a sales-
     *  velocity forecast. Every product at or below its reorder level shows up; good enough
     *  to flag "buy more of this soon" without needing sales-history modelling. */
    @Transactional(readOnly = true)
    public List<ReorderLine> reorderSuggestions(UUID storeId) {
        return listForStore(storeId).stream()
                .filter(r -> r.quantity() <= r.reorderLevel())
                .map(r -> new ReorderLine(r.productId(), r.productName(), r.sku(), r.quantity(),
                        r.reorderLevel(), Math.max(1, r.reorderLevel() * 2 - r.quantity())))
                .toList();
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
        boolean online = isOnlineShop(storeId);
        // Across the online shops, the last units may be sitting in a shopper's bag or a placed
        // order — the shelf count alone would let the till sell a frame already promised online.
        if (online && products.takeStock(productId, quantity) == 0) {
            throw ApiException.conflict("HELD_ONLINE",
                    "That stock is reserved for an online order right now — try again in 15 minutes or sell from another shop.");
        }
        apply(storeId, productId, -quantity, "SALE", saleReceiptNumber, null, userId, !online);
    }
}
