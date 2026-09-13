package com.specskart.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("select a from AuditLog a where " +
           "(:storeId is null or a.storeId = :storeId) and " +
           "(:entityType is null or a.entityType = :entityType) and " +
           "a.createdAt >= :from and a.createdAt < :to " +
           "order by a.createdAt desc")
    List<AuditLog> search(@Param("storeId") UUID storeId, @Param("entityType") String entityType,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
