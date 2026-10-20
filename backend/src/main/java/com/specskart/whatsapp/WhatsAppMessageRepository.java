package com.specskart.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, UUID> {
    List<WhatsAppMessage> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

    boolean existsByLeadIdAndMediaId(UUID leadId, String mediaId);
    long countByLeadId(UUID leadId);
    Optional<WhatsAppMessage> findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(UUID leadId, String direction);
    List<WhatsAppMessage> findByLeadIdAndDirectionAndCreatedAtAfter(UUID leadId, String direction, java.time.Instant after);
}
