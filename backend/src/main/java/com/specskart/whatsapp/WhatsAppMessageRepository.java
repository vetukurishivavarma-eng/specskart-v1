package com.specskart.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, UUID> {

    /** Leads that already have a message with this body (a broadcast logs "broadcast:<template>"). */
    @org.springframework.data.jpa.repository.Query("select distinct m.leadId from WhatsAppMessage m where m.body = :body")
    java.util.Set<UUID> leadIdsWithBody(@org.springframework.data.repository.query.Param("body") String body);
    List<WhatsAppMessage> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

    boolean existsByLeadIdAndMediaId(UUID leadId, String mediaId);
    long countByLeadId(UUID leadId);
    Optional<WhatsAppMessage> findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(UUID leadId, String direction);
    List<WhatsAppMessage> findByLeadIdAndDirectionAndCreatedAtAfter(UUID leadId, String direction, java.time.Instant after);
}
