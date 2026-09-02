package com.specskart.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {
    List<ConsentRecord> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
    boolean existsByLeadIdAndTypeAndStatus(UUID leadId, String type, String status);
}
