package com.specskart.lens;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LensInquiryRepository extends JpaRepository<LensInquiry, UUID> {
    Optional<LensInquiry> findByVerifyTokenHash(String hash);

    /** Has this WhatsApp number ever completed verification? A returning customer is not
     *  asked to prove the same number twice. */
    boolean existsByWaIdAndPhoneVerifiedAtIsNotNull(String waId);

    /** Verified but never reached SUBMITTED, never nudged, old enough to chase — the
     *  abandoned-lens-form recovery job's candidate pool. */
    List<LensInquiry> findTop50ByStatusAndNudgedAtIsNullAndCreatedAtBefore(String status, Instant before);

    /** Specskart POS: web orders ready for staff to collect payment on and hand over. */
    List<LensInquiry> findByStatusOrderByCreatedAtAsc(String status);

    List<LensInquiry> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(String status, Instant from, Instant to);

    /** Every web order not yet collected. `NOT IN (DELIVERED, CANCELLED)` also drops the
     *  walk-ins, whose fulfilment is null — nothing is queued at the counter. */
    List<LensInquiry> findByFulfilmentNotInOrderByCreatedAtAsc(java.util.Collection<String> fulfilment);

    /** The other half of that list: orders already handed over. Newest first, because a
     *  finished order is looked up to check something, not worked through in order. */
    List<LensInquiry> findByFulfilmentOrderByCreatedAtDesc(String fulfilment);

    Optional<LensInquiry> findByClientReference(String clientReference);

    /** The lead's latest web order, for the chatbot's "track my order". Walk-ins are handed
     *  over at the counter, so there is nothing to track. */
    Optional<LensInquiry> findTop1ByLeadIdAndWalkInFalseOrderByCreatedAtDesc(UUID leadId);

    /** All of them, for the "which order did you mean?" list. */
    List<LensInquiry> findByLeadIdAndWalkInFalseOrderByCreatedAtDesc(UUID leadId);

    /** Sold a few days ago, lead known, never thanked — the post-purchase job's candidate pool. */
    List<LensInquiry> findTop50ByStatusAndPostPurchaseAtIsNullAndLeadIdIsNotNullAndUpdatedAtBefore(
            String status, Instant before);
}
