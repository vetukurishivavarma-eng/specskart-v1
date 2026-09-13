package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Stock moving between shops. Creating a transfer decrements the source immediately
 *  (TRANSFER_OUT) — the goods have left — and stays "in transit" until the destination
 *  marks it received (TRANSFER_IN credits it there). Two separate movements rather than one,
 *  so stock isn't double-counted while it's physically on the road. */
@Service
public class TransferService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final TransferRepository transfers;
    private final TransferItemRepository transferItems;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final InventoryService inventory;

    public TransferService(TransferRepository transfers, TransferItemRepository transferItems,
                           StoreRepository stores, ProductRepository products, InventoryService inventory) {
        this.transfers = transfers;
        this.transferItems = transferItems;
        this.stores = stores;
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional
    public PosDtos.TransferView create(PosDtos.CreateTransfer req, UUID userId) {
        if (req.items() == null || req.items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_TRANSFER", "A transfer needs at least one item.");
        }
        if (req.fromStoreId() == null || req.fromStoreId().equals(req.toStoreId())) {
            throw ApiException.badRequest("BAD_STORES", "Pick two different shops.");
        }
        Store from = stores.findById(req.fromStoreId()).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store."));
        stores.findById(req.toStoreId()).orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No such store."));

        Transfer t = new Transfer();
        t.setReference(nextReference(from));
        t.setFromStoreId(req.fromStoreId());
        t.setToStoreId(req.toStoreId());
        t.setStatus("IN_TRANSIT");
        t.setNotes(req.notes() == null ? "" : req.notes());
        transfers.save(t);

        for (PosDtos.TransferItemRequest line : req.items()) {
            if (line.quantity() <= 0) throw ApiException.badRequest("BAD_QTY", "Quantity must be positive.");
            inventory.decrementForSale(req.fromStoreId(), line.productId(), line.quantity(), t.getReference(), userId);

            TransferItem item = new TransferItem();
            item.setTransferId(t.getId());
            item.setProductId(line.productId());
            item.setQuantity(line.quantity());
            transferItems.save(item);
        }
        return view(t);
    }

    @Transactional
    public PosDtos.TransferView receive(UUID id, UUID userId) {
        Transfer t = transfers.findById(id).orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "No such transfer."));
        if (!"IN_TRANSIT".equals(t.getStatus())) {
            throw ApiException.badRequest("NOT_IN_TRANSIT", "This transfer isn't awaiting receipt.");
        }
        for (TransferItem item : transferItems.findByTransferId(t.getId())) {
            inventory.adjust(t.getToStoreId(), item.getProductId(), item.getQuantity(), "TRANSFER_IN",
                    t.getReference(), null, userId);
        }
        t.setStatus("COMPLETED");
        return view(transfers.save(t));
    }

    @Transactional
    public PosDtos.TransferView cancel(UUID id, UUID userId) {
        Transfer t = transfers.findById(id).orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "No such transfer."));
        if (!"IN_TRANSIT".equals(t.getStatus())) {
            throw ApiException.badRequest("NOT_IN_TRANSIT", "Only a transfer still in transit can be cancelled.");
        }
        for (TransferItem item : transferItems.findByTransferId(t.getId())) {
            inventory.adjust(t.getFromStoreId(), item.getProductId(), item.getQuantity(), "TRANSFER_IN",
                    t.getReference(), "Transfer cancelled", userId);
        }
        t.setStatus("CANCELLED");
        return view(transfers.save(t));
    }

    @Transactional(readOnly = true)
    public List<PosDtos.TransferView> forStore(UUID storeId) {
        return transfers.findByFromStoreIdOrToStoreIdOrderByCreatedAtDesc(storeId, storeId).stream()
                .map(this::view).toList();
    }

    private String nextReference(Store from) {
        String day = DAY.format(Instant.now());
        return "TRF-%s-%s-%s".formatted(from.getCode(), day, transfers.count() + 1);
    }

    private PosDtos.TransferView view(Transfer t) {
        var items = transferItems.findByTransferId(t.getId()).stream()
                .map(i -> {
                    Product p = products.findById(i.getProductId()).orElse(null);
                    return new PosDtos.TransferItemView(i.getProductId(), p == null ? "Unknown product" : p.getName(), i.getQuantity());
                })
                .toList();
        return new PosDtos.TransferView(t.getId(), t.getReference(), t.getFromStoreId(), t.getToStoreId(),
                t.getStatus(), t.getNotes(), items, t.getCreatedAt());
    }
}
