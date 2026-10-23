package com.specskart.spin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpinRepository extends JpaRepository<Spin, UUID> {
    Optional<Spin> findFirstByLeadIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID leadId, Instant now);
    Optional<Spin> findFirstByFriendLeadIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID leadId, Instant now);
    List<Spin> findByLeadIdAndCreatedAtAfter(UUID leadId, Instant since);
    Optional<Spin> findByCodeIgnoreCase(String code);
    boolean existsByCode(String code);
    Optional<Spin> findByFriendCodeIgnoreCase(String code);
    boolean existsByFriendCode(String code);
    List<Spin> findTop100ByOrderByCreatedAtDesc();
}
