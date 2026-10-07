package com.specskart.pos;

import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

/** Staff manage their own POS devices; an admin can release anyone's, e.g. a lost phone. */
@RestController
@RequestMapping("/api/admin/pos/devices")
public class AdminDeviceController {

    public record DeviceView(UUID id, String deviceName, String platform, String appVersion,
                             Instant lastSeenAt, boolean active) {}
    /** A device plus whose login it holds, for the admin's all-devices list. */
    public record SignedInDeviceView(UUID id, String deviceName, String platform, String appVersion,
                                     Instant lastSeenAt, UUID userId, String userName, String userEmail) {}
    public record ReleaseRequest(String reason) {}

    private final DeviceSessionService devices;
    private final CurrentUser currentUser;
    private final UserRepository users;

    public AdminDeviceController(DeviceSessionService devices, CurrentUser currentUser, UserRepository users) {
        this.devices = devices;
        this.currentUser = currentUser;
        this.users = users;
    }

    /** Admin only: every phone signed in right now, across all accounts, so any stuck login
     *  (lost phone, swapped till, "already signed in on another device") is one tap to free. */
    @GetMapping
    public List<SignedInDeviceView> allActive(Authentication auth) {
        if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
        List<DeviceSession> active = devices.allActive();
        Map<UUID, User> byId = users.findAllById(active.stream().map(DeviceSession::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return active.stream().map(s -> {
            User u = byId.get(s.getUserId());
            return new SignedInDeviceView(s.getId(), s.getDeviceName(), s.getPlatform(), s.getAppVersion(),
                    s.getLastSeenAt(), s.getUserId(),
                    u == null ? null : u.getFullName(), u == null ? null : u.getEmail());
        }).toList();
    }

    @GetMapping("/me")
    public List<DeviceView> mine(Authentication auth) {
        UUID id = currentUser.idOf(auth);
        if (id == null) throw ApiException.notFound("USER_NOT_FOUND", "No such user.");
        return devices.forUser(id).stream().map(AdminDeviceController::view).toList();
    }

    /** Any admin can inspect any staff member's devices — the Staff screen's "view devices"
     *  action, e.g. checking why someone can't sign in. Not for a shop-scoped login: staff
     *  only ever see their own devices, via /me above. */
    @GetMapping("/user/{userId}")
    public List<DeviceView> forUser(@PathVariable UUID userId, Authentication auth) {
        if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
        return devices.forUser(userId).stream().map(AdminDeviceController::view).toList();
    }

    @PostMapping("/{sessionId}/release")
    public void release(@PathVariable UUID sessionId, @RequestBody(required = false) ReleaseRequest req, Authentication auth) {
        // Anyone can release their own device; only an admin can release someone else's.
        if (!currentUser.isAdmin(auth)) {
            boolean mine = devices.forUser(currentUser.idOf(auth)).stream().anyMatch(s -> s.getId().equals(sessionId));
            if (!mine) throw ApiException.forbidden("NOT_YOUR_DEVICE", "You can only release your own device.");
        }
        devices.release(sessionId, currentUser.idOf(auth), req == null ? null : req.reason());
    }

    private static DeviceView view(DeviceSession s) {
        return new DeviceView(s.getId(), s.getDeviceName(), s.getPlatform(), s.getAppVersion(),
                s.getLastSeenAt(), s.isActive());
    }
}
