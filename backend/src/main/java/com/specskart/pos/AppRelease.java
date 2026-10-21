package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A published build of the Specskart POS app. The app asks {@code GET /api/app/version}
 *  what the current build is on every launch/foreground — a shop running a build below
 *  {@code minimumBuild} is stopped at the door, matching NG POS's own forced-update rule. */
@Entity
// Declared here too so the create-drop test schema has prod's (V32) constraint.
@Table(name = "app_releases", uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "build_number"}))
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

    /**
     * A link never contains a space, but one pasted into the row can (2026-10-21: "...eng /specskart-pos..."
     * locked every till on a mandatory update it couldn't download). Stripped on the way in and out,
     * so a row already saved with spaces works too.
     */
    public String getDownloadUrl() {
        return downloadUrl == null ? null : downloadUrl.replaceAll("\\s+", "");
    }

    public void setDownloadUrl(String url) {
        this.downloadUrl = url == null ? null : url.replaceAll("\\s+", "");
    }

    @Column(nullable = false)
    private boolean mandatory = false;

    /** How many times a till may postpone this update before it is enforced. Ignored once
     *  {@link #mandatory} is set, or once the till is below {@link #minimumBuild}. */
    @Column(nullable = false)
    private int graceCount = 2;
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant publishedAt = Instant.now();
}
