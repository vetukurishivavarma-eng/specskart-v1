package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {
    /**
     * The release a till is offered. Sorted by publish time as well as build number, so
     * publishing the same build again corrects it: there is no edit or delete for a release,
     * and getting minimumBuild or mandatory wrong is otherwise unfixable without cutting a
     * whole new build. Newest row for the highest build wins.
     */
    Optional<AppRelease> findFirstByPlatformAndActiveTrueOrderByBuildNumberDescPublishedAtDesc(String platform);

    List<AppRelease> findByPlatformOrderByBuildNumberDescPublishedAtDesc(String platform);
}
