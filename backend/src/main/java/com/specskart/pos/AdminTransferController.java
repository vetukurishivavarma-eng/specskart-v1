package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/transfers")
public class AdminTransferController {

    private final TransferService transfers;
    private final TransferRepository transferRepo;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminTransferController(TransferService transfers, TransferRepository transferRepo,
                                   CurrentUser currentUser, AuditLogService audit) {
        this.transfers = transfers;
        this.transferRepo = transferRepo;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** A transfer touches two shops -- a shop-scoped login may act on it if it's the sender
     *  or the receiver, not just any third shop. */
    private void assertTouchesOwnStore(Authentication auth, UUID fromStoreId, UUID toStoreId) {
        UUID mine = currentUser.storeIdOf(auth);
        if (currentUser.isAdmin(auth) || mine == null) return;
        if (!mine.equals(fromStoreId) && !mine.equals(toStoreId)) {
            throw ApiException.forbidden("STORE_SCOPED", "You don't have access to this shop.");
        }
    }

    @PostMapping
    public PosDtos.TransferView create(@RequestBody PosDtos.CreateTransfer req, Authentication auth) {
        assertTouchesOwnStore(auth, req.fromStoreId(), req.toStoreId());
        PosDtos.TransferView t = transfers.create(req, currentUser.idOf(auth));
        audit.record("TRANSFER", t.id().toString(), "CREATE", currentUser.idOf(auth), currentUser.nameOf(auth),
                t.fromStoreId(), t.reference());
        return t;
    }

    @PostMapping("/{id}/receive")
    public PosDtos.TransferView receive(@PathVariable UUID id, Authentication auth) {
        Transfer t = transferRepo.findById(id).orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "No such transfer."));
        assertTouchesOwnStore(auth, t.getFromStoreId(), t.getToStoreId());
        PosDtos.TransferView view = transfers.receive(id, currentUser.idOf(auth));
        audit.record("TRANSFER", id.toString(), "RECEIVE", currentUser.idOf(auth), currentUser.nameOf(auth),
                view.toStoreId(), view.reference());
        return view;
    }

    @PostMapping("/{id}/cancel")
    public PosDtos.TransferView cancel(@PathVariable UUID id, Authentication auth) {
        Transfer t = transferRepo.findById(id).orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "No such transfer."));
        assertTouchesOwnStore(auth, t.getFromStoreId(), t.getToStoreId());
        PosDtos.TransferView view = transfers.cancel(id, currentUser.idOf(auth));
        audit.record("TRANSFER", id.toString(), "CANCEL", currentUser.idOf(auth), currentUser.nameOf(auth),
                view.fromStoreId(), view.reference());
        return view;
    }

    @GetMapping
    public List<PosDtos.TransferView> forStore(@RequestParam UUID storeId, Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        return transfers.forStore(storeId);
    }
}
