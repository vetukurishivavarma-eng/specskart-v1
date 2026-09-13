package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {
    List<DeviceSession> findByUserIdAndRevokedAtIsNull(UUID userId);
    Optional<DeviceSession> findByUserIdAndDeviceIdAndRevokedAtIsNull(UUID userId, String deviceId);
    List<DeviceSession> findByUserIdOrderByLastSeenAtDesc(UUID userId);
}
