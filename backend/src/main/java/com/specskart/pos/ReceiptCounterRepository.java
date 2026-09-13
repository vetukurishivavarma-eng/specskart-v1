package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptCounterRepository extends JpaRepository<ReceiptCounter, UUID> {
    Optional<ReceiptCounter> findByStoreIdAndSaleDay(UUID storeId, String saleDay);
}
