package com.specskart.lens;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LensInquiryRepository extends JpaRepository<LensInquiry, UUID> {
    Optional<LensInquiry> findByVerifyTokenHash(String hash);

    /** Verified but never reached SUBMITTED, never nudged, old enough to chase — the
     *  abandoned-lens-form recovery job's candidate pool. */
    List<LensInquiry> findTop50ByStatusAndNudgedAtIsNullAndCreatedAtBefore(String status, Instant before);
}
