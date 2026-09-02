package com.specskart.faceanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FaceAnalysisRepository extends JpaRepository<FaceAnalysis, UUID> {
    List<FaceAnalysis> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
    Optional<FaceAnalysis> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);
    long countByConsentGivenTrue();
}
