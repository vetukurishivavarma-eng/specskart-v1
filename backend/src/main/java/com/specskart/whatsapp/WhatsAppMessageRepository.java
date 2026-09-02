package com.specskart.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, UUID> {
    List<WhatsAppMessage> findByLeadIdOrderByCreatedAtAsc(UUID leadId);
    long countByLeadId(UUID leadId);
}
