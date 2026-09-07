package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockAlertRepository extends JpaRepository<StockAlert, UUID> {

    Optional<StockAlert> findByProductIdAndWaId(UUID productId, String waId);

    List<StockAlert> findByNotifiedAtIsNull();
}
