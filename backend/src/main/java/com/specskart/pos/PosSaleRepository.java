package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {
    Optional<PosSale> findByClientReference(String clientReference);
    List<PosSale> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID storeId, Instant from, Instant to);

    long countByStoreId(UUID storeId);
}
