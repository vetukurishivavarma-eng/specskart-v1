package com.specskart.membership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /** The lead's live membership, if they have one. Unpaid rows are abandoned checkouts. */
    Optional<Membership> findFirstByLeadIdAndPaidAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(
            UUID leadId, Instant now);
}
