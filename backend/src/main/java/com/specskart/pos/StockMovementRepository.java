package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    List<StockMovement> findByStoreIdOrderByCreatedAtDesc(UUID storeId);
    List<StockMovement> findByStoreIdAndProductIdOrderByCreatedAtDesc(UUID storeId, UUID productId);
}
