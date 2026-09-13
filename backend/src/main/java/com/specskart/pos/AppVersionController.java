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

    public record VersionInfo(String version, int buildNumber, int minimumBuild, String downloadUrl,
                              String notes, boolean mandatory) {}

    private final AppReleaseRepository releases;

    public AppVersionController(AppReleaseRepository releases) {
        this.releases = releases;
    }

    @GetMapping("/version")
    public VersionInfo version(@RequestParam(defaultValue = "android") String platform) {
        return releases.findFirstByPlatformAndActiveTrueOrderByBuildNumberDesc(platform)
                .map(r -> new VersionInfo(r.getVersion(), r.getBuildNumber(), r.getMinimumBuild(),
                        r.getDownloadUrl(), r.getNotes(), r.isMandatory()))
                .orElse(new VersionInfo("1.0.0", 1, 0, "", "", false));
    }
}
