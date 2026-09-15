package com.specskart.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalkInVerificationRepository extends JpaRepository<WalkInVerification, UUID> {
    Optional<WalkInVerification> findByCode(String code);

    boolean existsByCode(String code);
}
