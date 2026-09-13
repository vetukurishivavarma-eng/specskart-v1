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

    public record UpdateStore(String name, String city, Boolean active) {}

    private final StoreRepository stores;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminStoreController(StoreRepository stores, CurrentUser currentUser, AuditLogService audit) {
        this.stores = stores;
        this.currentUser = currentUser;
        this.audit = audit;
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
        if (req.active() != null) s.setActive(req.active());
        stores.save(s);
        audit.record("STORE", id.toString(), "UPDATE", currentUser.idOf(auth), currentUser.nameOf(auth), id, s.getName());
        return view(s);
    }

    private void requireAdmin(Authentication auth) {
        if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
    }

    private static PosDtos.StoreView view(Store s) {
        return new PosDtos.StoreView(s.getId(), s.getName(), s.getCode(), s.getCity(), s.isActive());
    }
}
