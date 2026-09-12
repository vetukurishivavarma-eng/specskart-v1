package com.specskart.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByWhatsappWaId(String waId);

    /** Leads whose next nurture touch is due (job picks these up each hour). */
    List<Lead> findTop50ByFollowUpStateAndFollowUpNextAtLessThanEqualOrderByFollowUpNextAtAsc(
            FollowUpState state, Instant when);

    /** Cold leads never enrolled in the nurture sequence, for the one-time backfill. */
    List<Lead> findTop100ByFollowUpStateIsNullAndArchivedAtIsNullAndStatusInAndCreatedAtAfter(
            java.util.Collection<LeadStatus> statuses, Instant since);

    /** Broadcast candidate pool — bounded so an admin blast can never accidentally table-scan
     *  a huge lead base. Further filtered (opt-out, face shape, has a WhatsApp id) in-memory. */
    List<Lead> findTop1000ByArchivedAtIsNullOrderByLastContactAtDesc();
    Optional<Lead> findByWhatsappNumber(String number);
    Optional<Lead> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);

    long countByCreatedAtAfter(Instant since);
    long countByStatus(LeadStatus status);
    long countByCampaignId(UUID campaignId);

    // `cast(:q as string)` is load-bearing on PostgreSQL: a bare null bind is
    // untyped, so `lower(concat('%', :q, '%'))` resolves to `lower(bytea)` and
    // the query 500s. The cast gives the driver a varchar to bind. The OR group
    // is parenthesised so the `:q is null` short-circuit covers both LIKEs.
    @Query("""
        select l from Lead l
        where (:archived = true and l.archivedAt is not null
               or :archived = false and l.archivedAt is null)
          and (:status is null or l.status = :status)
          and (:campaignId is null or l.campaignId = :campaignId)
          and (:q is null
               or lower(l.name) like lower(concat('%', cast(:q as string), '%'))
               or l.whatsappNumber like concat('%', cast(:q as string), '%'))
        """)
    Page<Lead> search(@Param("status") LeadStatus status,
                      @Param("campaignId") UUID campaignId,
                      @Param("q") String q,
                      @Param("archived") boolean archived,
                      Pageable pageable);
}
