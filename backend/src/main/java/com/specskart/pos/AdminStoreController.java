package com.specskart.pos;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/stores")
public class AdminStoreController {

    private final StoreRepository stores;

    public AdminStoreController(StoreRepository stores) {
        this.stores = stores;
    }

    @GetMapping
    public List<PosDtos.StoreView> list() {
        return stores.findAllByOrderByNameAsc().stream().map(AdminStoreController::view).toList();
    }

    @PostMapping
    public PosDtos.StoreView create(@RequestBody PosDtos.CreateStore req) {
        Store s = new Store();
        s.setName(req.name());
        s.setCode(req.code());
        s.setCity(req.city() == null ? "" : req.city());
        return view(stores.save(s));
    }

    @PatchMapping("/{id}")
    public PosDtos.StoreView setActive(@PathVariable UUID id, @RequestParam boolean active) {
        Store s = stores.findById(id).orElseThrow();
        s.setActive(active);
        return view(stores.save(s));
    }

    private static PosDtos.StoreView view(Store s) {
        return new PosDtos.StoreView(s.getId(), s.getName(), s.getCode(), s.getCity(), s.isActive());
    }
}
