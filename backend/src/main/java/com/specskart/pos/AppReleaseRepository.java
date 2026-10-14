package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {
    /**
     * The release a till is offered: the highest active build. Publishing the same build
     * again overwrites its row (see AdminAppReleaseController.publish), which is how a wrong
     * URL, minimumBuild or mandatory flag is corrected without cutting a new build.
     */
    Optional<AppRelease> findFirstByPlatformAndActiveTrueOrderByBuildNumberDescPublishedAtDesc(String platform);

    List<AppRelease> findByPlatformOrderByBuildNumberDescPublishedAtDesc(String platform);

    Optional<AppRelease> findByPlatformAndBuildNumber(String platform, int buildNumber);
}
