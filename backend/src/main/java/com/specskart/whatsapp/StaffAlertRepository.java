package com.specskart.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAlertRepository extends JpaRepository<StaffAlert, UUID> {

    boolean existsByKindAndRefIdAndRecipient(String kind, UUID refId, String recipient);

    List<StaffAlert> findByKindAndRefId(String kind, UUID refId);

    Optional<StaffAlert> findFirstByWamid(String wamid);

    List<StaffAlert> findTop50ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAt(
            Collection<StaffAlert.Status> statuses, Instant before);
}
