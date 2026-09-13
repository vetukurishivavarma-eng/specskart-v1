package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    List<Transfer> findByFromStoreIdOrToStoreIdOrderByCreatedAtDesc(UUID fromStoreId, UUID toStoreId);
    List<Transfer> findByStatusOrderByCreatedAtAsc(String status);
}
