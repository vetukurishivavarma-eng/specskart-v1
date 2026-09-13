package com.specskart.pos;

import com.specskart.catalog.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/stores/{storeId}/movements")
public class AdminStockMovementController {

    public record MovementView(UUID productId, String productName, String type, int quantity,
                               int balance, String reference, String note, Instant createdAt) {}

    private final StockMovementRepository movements;
    private final ProductRepository products;

    public AdminStockMovementController(StockMovementRepository movements, ProductRepository products) {
        this.movements = movements;
        this.products = products;
    }

    @GetMapping
    public List<MovementView> list(@PathVariable UUID storeId) {
        return movements.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .limit(200)
                .map(m -> new MovementView(m.getProductId(),
                        products.findById(m.getProductId()).map(p -> p.getName()).orElse("Unknown product"),
                        m.getMovementType(), m.getQuantity(), m.getBalance(), m.getReference(), m.getNote(), m.getCreatedAt()))
                .toList();
    }
}
