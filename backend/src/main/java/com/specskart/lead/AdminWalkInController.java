package com.specskart.lead;

import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** POS app: "Add customer" — show a WhatsApp QR, poll until the customer has sent it. */
@RestController
@RequestMapping("/api/admin/pos/walk-ins")
public class AdminWalkInController {

    public record StartRequest(UUID storeId, String customerName) {}

    private final WalkInService walkIns;
    private final CurrentUser currentUser;

    public AdminWalkInController(WalkInService walkIns, CurrentUser currentUser) {
        this.walkIns = walkIns;
        this.currentUser = currentUser;
    }

    @PostMapping
    public WalkInService.Started start(@RequestBody StartRequest req, Authentication auth) {
        UUID store = req.storeId() != null ? req.storeId() : currentUser.storeIdOf(auth);
        if (store != null) currentUser.assertStoreAccess(auth, store);
        return walkIns.start(store, req.customerName(), currentUser.idOf(auth));
    }

    @GetMapping("/{id}")
    public WalkInService.Status status(@PathVariable UUID id, Authentication auth) {
        WalkInService.Status s = walkIns.status(id);
        if (s.storeId() != null) currentUser.assertStoreAccess(auth, s.storeId());
        return s;
    }
}
