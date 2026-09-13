package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {
    Optional<AppRelease> findFirstByPlatformAndActiveTrueOrderByBuildNumberDesc(String platform);
    List<AppRelease> findByPlatformOrderByBuildNumberDesc(String platform);
}
