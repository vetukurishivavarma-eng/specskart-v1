package com.specskart.framefinder;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FrameFinderSessionRepository extends JpaRepository<FrameFinderSession, UUID> {
    Optional<FrameFinderSession> findByTokenHash(String tokenHash);
    long countByStatus(com.specskart.framefinder.FrameFinderSessionStatus status);
}
