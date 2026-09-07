package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    List<PromoCode> findByActiveTrueAndAutoIssueTrue();

    List<PromoCode> findAllByOrderByCreatedAtDesc();
}
