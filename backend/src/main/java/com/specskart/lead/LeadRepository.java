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

    /**
     * Customers due an eye-test recall: they bought something older than the cutoff, they haven't
     * opted out, and we either never nudged them or last did so before the cutoff too. Ordered
     * oldest-first so a backlog drains in the order people actually bought.
     *
     * <p>A "purchase" is a delivered frames order <em>or</em> a sold lens inquiry. Lens buyers are
     * the ones this matters most for — a prescription is exactly the thing that goes stale in a
     * year — and they are the whole of the funnel the client currently has switched on.
     */
    @Query("""
            select distinct l from Lead l
            where (exists (
                    select 1 from com.specskart.order.Order o
                    where o.leadId = l.id
                      and o.status = com.specskart.order.OrderStatus.DELIVERED
                      and o.createdAt <= :cutoff)
                or exists (
                    select 1 from com.specskart.lens.LensInquiry q
                    where q.leadId = l.id
                      and q.status = 'SOLD'
                      and q.createdAt <= :cutoff))
              and l.archivedAt is null
              and (l.followUpState is null or l.followUpState <> com.specskart.lead.FollowUpState.OPTED_OUT)
              and (l.eyeTestRecalledAt is null or l.eyeTestRecalledAt <= :cutoff)
            order by l.firstContactAt asc
            """)
    List<Lead> findDueForEyeTestRecall(@Param("cutoff") Instant cutoff, Pageable page);

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
          and (:source is null or l.acquisitionSource = :source)
          and (:q is null
               or lower(l.name) like lower(concat('%', cast(:q as string), '%'))
               or l.whatsappNumber like concat('%', cast(:q as string), '%'))
        """)
    Page<Lead> search(@Param("status") LeadStatus status,
                      @Param("campaignId") UUID campaignId,
                      @Param("source") AcquisitionSource source,
                      @Param("q") String q,
                      @Param("archived") boolean archived,
                      Pageable pageable);
}
