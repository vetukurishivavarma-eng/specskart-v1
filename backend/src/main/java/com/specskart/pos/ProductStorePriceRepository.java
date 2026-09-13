package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductStorePriceRepository extends JpaRepository<ProductStorePrice, UUID> {
    Optional<ProductStorePrice> findByStoreIdAndProductId(UUID storeId, UUID productId);
}
