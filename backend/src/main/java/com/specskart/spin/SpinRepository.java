package com.specskart.spin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpinRepository extends JpaRepository<Spin, UUID> {
    Optional<Spin> findByLeadId(UUID leadId);
    Optional<Spin> findByCodeIgnoreCase(String code);
    boolean existsByCode(String code);
    Optional<Spin> findByFriendCodeIgnoreCase(String code);
    boolean existsByFriendCode(String code);
    boolean existsByFriendLeadId(UUID leadId);
    List<Spin> findTop100ByOrderByCreatedAtDesc();
}
