package com.specskart.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LeadEventRepository extends JpaRepository<LeadEvent, UUID> {
    List<LeadEvent> findByLeadIdOrderByCreatedAtAsc(UUID leadId);
    long countByEventType(LeadEventType type);
    long countByEventTypeAndCampaignId(LeadEventType type, UUID campaignId);
}
