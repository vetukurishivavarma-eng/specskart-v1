package com.specskart.pos;

import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff manage their own POS devices; an admin can release anyone's, e.g. a lost phone. */
@RestController
@RequestMapping("/api/admin/pos/devices")
public class AdminDeviceController {

    public record DeviceView(UUID id, String deviceName, String platform, String appVersion,
                             Instant lastSeenAt, boolean active) {}
    public record ReleaseRequest(String reason) {}

    private final DeviceSessionService devices;
    private final CurrentUser currentUser;

    public AdminDeviceController(DeviceSessionService devices, CurrentUser currentUser) {
        this.devices = devices;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public List<DeviceView> mine(Authentication auth) {
        UUID id = currentUser.idOf(auth);
        if (id == null) throw ApiException.notFound("USER_NOT_FOUND", "No such user.");
        return devices.forUser(id).stream().map(AdminDeviceController::view).toList();
    }

    @PostMapping("/{sessionId}/release")
    public void release(@PathVariable UUID sessionId, @RequestBody(required = false) ReleaseRequest req, Authentication auth) {
        devices.release(sessionId, currentUser.idOf(auth), req == null ? null : req.reason());
    }

    private static DeviceView view(DeviceSession s) {
        return new DeviceView(s.getId(), s.getDeviceName(), s.getPlatform(), s.getAppVersion(),
                s.getLastSeenAt(), s.isActive());
    }
}
