package com.specskart.pos;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Publishing a release is a plain admin action here — this app builds its APK via GitHub
 *  Actions (see the specskart-pos repo's build-apk.yml) and hands staff the artifact/release
 *  link manually, so this just records that a build exists and whether older ones are still
 *  allowed to run. */
@RestController
@RequestMapping("/api/admin/pos/releases")
public class AdminAppReleaseController {

    public record PublishRelease(String platform, String version, int buildNumber, int minimumBuild,
                                 String downloadUrl, String notes, boolean mandatory) {}

    private final AppReleaseRepository releases;

    public AdminAppReleaseController(AppReleaseRepository releases) {
        this.releases = releases;
    }

    @GetMapping
    public List<AppRelease> list(@RequestParam(defaultValue = "android") String platform) {
        return releases.findByPlatformOrderByBuildNumberDesc(platform);
    }

    @PostMapping
    public AppRelease publish(@RequestBody PublishRelease req) {
        AppRelease r = new AppRelease();
        r.setPlatform(req.platform() == null ? "android" : req.platform());
        r.setVersion(req.version());
        r.setBuildNumber(req.buildNumber());
        r.setMinimumBuild(req.minimumBuild());
        r.setDownloadUrl(req.downloadUrl());
        r.setNotes(req.notes() == null ? "" : req.notes());
        r.setMandatory(req.mandatory());
        return releases.save(r);
    }
}
