package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/stores")
public class AdminStoreController {

    /** location: "lat, lng" (or a pasted Google Maps link) sets the shop's pin, "" removes it. */
    public record UpdateStore(String name, String city, Boolean active, String location, String address) {}

    private static final java.util.regex.Pattern LAT_LNG =
            java.util.regex.Pattern.compile("(-?\\d{1,2}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)");

    private final StoreRepository stores;
    private final CurrentUser currentUser;
    private final AuditLogService audit;
    private final InventoryService inventory;
    private final PosSaleRepository sales;
    private final ProductInventoryRepository shelves;

    public AdminStoreController(StoreRepository stores, CurrentUser currentUser, AuditLogService audit,
                                InventoryService inventory, PosSaleRepository sales,
                                ProductInventoryRepository shelves) {
        this.inventory = inventory;
        this.stores = stores;
        this.currentUser = currentUser;
        this.audit = audit;
        this.sales = sales;
        this.shelves = shelves;
    }

    /**
     * Removes a shop that was created by mistake.
     *
     * Only one that never traded. A shop with sales against it is woven through the reports,
     * the stock movements and the audit log, and deleting it would leave those reading from a
     * row that no longer exists — so that one is deactivated instead, which already hides it
     * everywhere a working shop appears. Stock on its shelves is the same argument in advance:
     * it is somebody's counted inventory, not a mistake.
     */
    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> delete(@PathVariable UUID id, Authentication auth) {
        requireAdmin(auth);
        Store s = stores.findById(id).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such shop."));

        long sold = sales.countByStoreId(id);
        if (sold > 0) {
            throw ApiException.badRequest("STORE_HAS_SALES",
                    "\"" + s.getName() + "\" has " + sold + " sale(s) against it, so its reports still need it."
                            + " Switch it off instead of deleting it.");
        }
        boolean stocked = shelves.findByStoreId(id).stream().anyMatch(r -> r.getQuantity() != 0);
        if (stocked) {
            throw ApiException.badRequest("STORE_HAS_STOCK",
                    "\"" + s.getName() + "\" still has stock on its shelves. Move or zero it first,"
                            + " or switch the shop off instead.");
        }

        // Empty shelf rows (a product touched then set back to zero) carry no history worth keeping.
        shelves.deleteAll(shelves.findByStoreId(id));
        stores.delete(s);
        audit.record("STORE", id.toString(), "DELETE", currentUser.idOf(auth), currentUser.nameOf(auth), id, s.getName());
        inventory.resyncWebStock();
        return java.util.Map.of("deleted", id);
    }

    /** A shop-scoped login only ever needs to see its own shop (e.g. the store-picker) --
     *  everyone else's name/city isn't this login's business. Admin sees all. */
    @GetMapping
    public List<PosDtos.StoreView> list(Authentication auth) {
        UUID mine = currentUser.storeIdOf(auth);
        var all = stores.findAllByOrderByNameAsc().stream().map(AdminStoreController::view).toList();
        if (currentUser.isAdmin(auth) || mine == null) return all;
        return all.stream().filter(s -> s.id().equals(mine)).toList();
    }

    @GetMapping("/{id}")
    public PosDtos.StoreView get(@PathVariable UUID id, Authentication auth) {
        currentUser.assertStoreAccess(auth, id);
        return view(stores.findById(id).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store.")));
    }

    @PostMapping
    public PosDtos.StoreView create(@RequestBody PosDtos.CreateStore req, Authentication auth) {
        requireAdmin(auth);
        Store s = new Store();
        s.setName(req.name());
        s.setCode(req.code());
        s.setCity(req.city() == null ? "" : req.city());
        stores.save(s);
        audit.record("STORE", s.getId().toString(), "CREATE", currentUser.idOf(auth), currentUser.nameOf(auth), s.getId(), s.getName());
        return view(s);
    }

    @PutMapping("/{id}")
    public PosDtos.StoreView update(@PathVariable UUID id, @RequestBody UpdateStore req, Authentication auth) {
        requireAdmin(auth);
        Store s = stores.findById(id).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store."));
        if (req.name() != null) s.setName(req.name());
        if (req.city() != null) s.setCity(req.city());
        if (req.address() != null) s.setAddress(req.address().isBlank() ? null : req.address().trim());
        if (req.active() != null) s.setActive(req.active());
        if (req.location() != null) {
            double[] ll = parseLocation(req.location());
            s.setLatitude(ll == null ? null : ll[0]);
            s.setLongitude(ll == null ? null : ll[1]);
        }
        stores.save(s);
        // a shop starting/stopping online selling changes what the website has in stock
        if (req.active() != null || req.location() != null) inventory.resyncWebStock();
        audit.record("STORE", id.toString(), "UPDATE", currentUser.idOf(auth), currentUser.nameOf(auth), id, s.getName());
        return view(s);
    }

    static double[] parseLocation(String raw) {
        if (raw.isBlank()) return null;
        var m = LAT_LNG.matcher(raw);
        if (m.find()) {
            double lat = Double.parseDouble(m.group(1)), lng = Double.parseDouble(m.group(2));
            if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) return new double[]{lat, lng};
        }
        throw ApiException.badRequest("BAD_LOCATION",
                "Paste the shop's coordinates from Google Maps, e.g. -15.4167, 28.2833");
    }

    private void requireAdmin(Authentication auth) {
        if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
    }

    private static PosDtos.StoreView view(Store s) {
        return new PosDtos.StoreView(s.getId(), s.getName(), s.getCode(), s.getCity(), s.isActive(),
                s.getLatitude(), s.getLongitude(), s.getAddress());
    }
}
