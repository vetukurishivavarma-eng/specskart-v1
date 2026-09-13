package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, UUID> {
    Optional<ProductInventory> findByStoreIdAndProductId(UUID storeId, UUID productId);
    List<ProductInventory> findByStoreId(UUID storeId);
    List<ProductInventory> findByStoreIdAndQuantityLessThanEqual(UUID storeId, int quantity);
}
