package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A published build of the Specskart POS app. The app asks {@code GET /api/app/version}
 *  what the current build is on every launch/foreground — a shop running a build below
 *  {@code minimumBuild} is stopped at the door, matching NG POS's own forced-update rule. */
@Entity
@Table(name = "app_releases")
@Getter
@Setter
public class AppRelease extends BaseEntity {

    @Column(nullable = false)
    private String platform = "android";

    /** What a person reads, e.g. "1.1.0". */
    @Column(nullable = false)
    private String version;
    /** What the app compares — an integer that only goes up. */
    @Column(nullable = false)
    private int buildNumber;
    /** Any build below this may not be used at all. */
    @Column(nullable = false)
    private int minimumBuild = 0;

    /** Where the APK is — the GitHub Actions artifact/release link; opened in the browser. */
    @Column(nullable = false)
    private String downloadUrl;
    @Column(nullable = false)
    private String notes = "";

    @Column(nullable = false)
    private boolean mandatory = false;
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant publishedAt = Instant.now();
}
