package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosSaleItemRepository extends JpaRepository<PosSaleItem, UUID> {
    List<PosSaleItem> findBySaleId(UUID saleId);
}
