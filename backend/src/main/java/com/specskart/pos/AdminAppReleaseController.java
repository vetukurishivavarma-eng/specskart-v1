package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
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
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminAppReleaseController(AppReleaseRepository releases, CurrentUser currentUser, AuditLogService audit) {
        this.releases = releases;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping
    public List<AppRelease> list(@RequestParam(defaultValue = "android") String platform) {
        return releases.findByPlatformOrderByBuildNumberDesc(platform);
    }

    @PostMapping
    public AppRelease publish(@RequestBody PublishRelease req, Authentication auth) {
        if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
        AppRelease r = new AppRelease();
        r.setPlatform(req.platform() == null ? "android" : req.platform());
        r.setVersion(req.version());
        r.setBuildNumber(req.buildNumber());
        r.setMinimumBuild(req.minimumBuild());
        r.setDownloadUrl(req.downloadUrl());
        r.setNotes(req.notes() == null ? "" : req.notes());
        r.setMandatory(req.mandatory());
        releases.save(r);
        audit.record("APP_RELEASE", r.getId().toString(), "PUBLISH", currentUser.idOf(auth), currentUser.nameOf(auth),
                null, "v" + r.getVersion() + " build " + r.getBuildNumber());
        return r;
    }
}
