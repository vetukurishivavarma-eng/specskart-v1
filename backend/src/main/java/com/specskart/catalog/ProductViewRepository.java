package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductViewRepository extends JpaRepository<ProductView, UUID> {
    Optional<ProductView> findByLeadIdAndProductId(UUID leadId, UUID productId);

    List<ProductView> findTop50ByNotifiedAtIsNullAndViewedAtBetweenOrderByViewedAtAsc(Instant from, Instant to);
}
