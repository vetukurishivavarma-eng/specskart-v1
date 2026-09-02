package com.specskart.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByWhatsappWaId(String waId);
    Optional<Lead> findByWhatsappNumber(String number);

    long countByCreatedAtAfter(Instant since);
    long countByStatus(LeadStatus status);
    long countByCampaignId(UUID campaignId);

    @Query("""
        select l from Lead l
        where (:status is null or l.status = :status)
          and (:campaignId is null or l.campaignId = :campaignId)
          and (:q is null or lower(l.name) like lower(concat('%', :q, '%'))
               or l.whatsappNumber like concat('%', :q, '%'))
        """)
    Page<Lead> search(@Param("status") LeadStatus status,
                      @Param("campaignId") UUID campaignId,
                      @Param("q") String q,
                      Pageable pageable);
}
