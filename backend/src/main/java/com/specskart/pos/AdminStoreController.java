package com.specskart.pos;

import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/stores")
public class AdminStoreController {

    public record UpdateStore(String name, String city, Boolean active) {}

    private final StoreRepository stores;

    public AdminStoreController(StoreRepository stores) {
        this.stores = stores;
    }

    @GetMapping
    public List<PosDtos.StoreView> list() {
        return stores.findAllByOrderByNameAsc().stream().map(AdminStoreController::view).toList();
    }

    @GetMapping("/{id}")
    public PosDtos.StoreView get(@PathVariable UUID id) {
        return view(stores.findById(id).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store.")));
    }

    @PostMapping
    public PosDtos.StoreView create(@RequestBody PosDtos.CreateStore req) {
        Store s = new Store();
        s.setName(req.name());
        s.setCode(req.code());
        s.setCity(req.city() == null ? "" : req.city());
        return view(stores.save(s));
    }

    @PutMapping("/{id}")
    public PosDtos.StoreView update(@PathVariable UUID id, @RequestBody UpdateStore req) {
        Store s = stores.findById(id).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store."));
        if (req.name() != null) s.setName(req.name());
        if (req.city() != null) s.setCity(req.city());
        if (req.active() != null) s.setActive(req.active());
        return view(stores.save(s));
    }

    private static PosDtos.StoreView view(Store s) {
        return new PosDtos.StoreView(s.getId(), s.getName(), s.getCode(), s.getCity(), s.isActive());
    }
}
