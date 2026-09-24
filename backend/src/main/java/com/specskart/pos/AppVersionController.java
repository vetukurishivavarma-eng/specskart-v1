package com.specskart.pos;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated — the app asks this before anyone's signed in, so there's nobody to scope
 *  it by. Reuses /api/public/** (already permitAll in SecurityConfig) rather than adding a
 *  new security rule for one endpoint. */
@RestController
@RequestMapping("/api/public/app")
public class AppVersionController {

    /**
     * What the till is told about the current build.
     *
     * The first six fields are the original shape and must stay exactly as they are: builds 7
     * and older read them directly and decide for themselves whether to block. The rest are
     * for build 8 onward, which lets the server do the deciding — including how many times
     * "Later" may be tapped.
     */
    public record VersionInfo(String version, int buildNumber, int minimumBuild, String downloadUrl,
                              String notes, boolean mandatory,
                              boolean updateAvailable, int graceCount, Integer currentBuild) {}

    private final AppReleaseRepository releases;

    public AppVersionController(AppReleaseRepository releases) {
        this.releases = releases;
    }

    /**
     * @param build the till's installed versionCode. Absent from build 7 and older, which sent
     *              only the platform; treated as 0, which is harmless because those builds
     *              ignore everything this method derives from it and compare for themselves.
     */
    @GetMapping("/version")
    public VersionInfo version(@RequestParam(defaultValue = "android") String platform,
                               @RequestParam(required = false) Integer build) {
        AppRelease latest = releases.findFirstByPlatformAndActiveTrueOrderByBuildNumberDescPublishedAtDesc(platform)
                .orElse(null);
        // Nothing published yet. Answering "you are current" rather than 404 is deliberate: a
        // server with no release row must never lock a shop out of its till, and the app treats
        // a failed check the same way.
        if (latest == null) {
            return new VersionInfo("1.0.0", 1, 0, "", "", false, false, 0, build);
        }

        int installed = build == null ? 0 : build;
        boolean updateAvailable = installed < latest.getBuildNumber();
        // Compulsory two ways: the release says so, or the till is below the floor it sets. The
        // second covers a build that must not keep running at all — a pricing bug, a broken
        // sync — without waiting for the shop to have spent its postponements.
        boolean mandatory = updateAvailable && (latest.isMandatory() || installed < latest.getMinimumBuild());

        return new VersionInfo(latest.getVersion(), latest.getBuildNumber(), latest.getMinimumBuild(),
                latest.getDownloadUrl(), latest.getNotes(),
                // The RAW flag, not the computed one: build 7 pairs it with its own
                // minimumBuild comparison, and handing it a true derived from the floor would
                // make it block twice over. Build 8 gets the floor through graceCount = 0,
                // which its "blocked = mandatory || used >= graceCount" resolves the same way.
                latest.isMandatory(),
                updateAvailable, mandatory ? 0 : latest.getGraceCount(), build);
    }
}
