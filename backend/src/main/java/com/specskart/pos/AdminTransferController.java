package com.specskart.pos;

import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/transfers")
public class AdminTransferController {

    private final TransferService transfers;
    private final CurrentUser currentUser;

    public AdminTransferController(TransferService transfers, CurrentUser currentUser) {
        this.transfers = transfers;
        this.currentUser = currentUser;
    }

    @PostMapping
    public PosDtos.TransferView create(@RequestBody PosDtos.CreateTransfer req, Authentication auth) {
        return transfers.create(req, currentUser.idOf(auth));
    }

    @PostMapping("/{id}/receive")
    public PosDtos.TransferView receive(@PathVariable UUID id, Authentication auth) {
        return transfers.receive(id, currentUser.idOf(auth));
    }

    @PostMapping("/{id}/cancel")
    public PosDtos.TransferView cancel(@PathVariable UUID id, Authentication auth) {
        return transfers.cancel(id, currentUser.idOf(auth));
    }

    @GetMapping
    public List<PosDtos.TransferView> forStore(@RequestParam UUID storeId) {
        return transfers.forStore(storeId);
    }
}
