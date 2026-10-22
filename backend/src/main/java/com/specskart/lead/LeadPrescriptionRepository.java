package com.specskart.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadPrescriptionRepository extends JpaRepository<LeadPrescription, UUID> {
    List<LeadPrescription> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
    Optional<LeadPrescription> findByLeadIdAndMediaId(UUID leadId, String mediaId);
}
