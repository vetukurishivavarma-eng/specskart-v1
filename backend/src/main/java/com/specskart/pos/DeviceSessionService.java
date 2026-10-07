package com.specskart.pos;

import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One till, one account, one device — see {@link DeviceSession}. Sign-in claims a device;
 *  a second device is refused while one is still active, and an administrator releases it. */
@Service
public class DeviceSessionService {

    private final DeviceSessionRepository sessions;

    public DeviceSessionService(DeviceSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Called at login. Refuses a second device while the account already has one active,
     *  unless it's the same device signing back in. */
    @Transactional
    public DeviceSession claim(UUID userId, String deviceId, String deviceName, String platform, String appVersion) {
        List<DeviceSession> active = sessions.findByUserIdAndRevokedAtIsNull(userId);
        for (DeviceSession existing : active) {
            if (existing.getDeviceId().equals(deviceId)) {
                existing.setLastSeenAt(Instant.now());
                existing.setAppVersion(appVersion);
                return sessions.save(existing);
            }
        }
        if (!active.isEmpty()) {
            throw ApiException.conflict("DEVICE_CONFLICT",
                    "Already signed in on another device. Ask an administrator to release it.");
        }
        DeviceSession s = new DeviceSession();
        s.setUserId(userId);
        s.setDeviceId(deviceId);
        s.setDeviceName(deviceName);
        s.setPlatform(platform == null ? "android" : platform);
        s.setAppVersion(appVersion);
        s.setLastSeenAt(Instant.now());
        return sessions.save(s);
    }

    /**
     * Every authenticated POS request (X-Device-Id / X-App-Version headers). Without this the
     * Devices screen only learned the version at sign-in, so a phone that updated in place kept
     * showing the old one for up to 30 days. Writes only when the version changed or the
     * last-seen time is more than {@link #SEEN_EVERY} old, so it isn't a write per request.
     */
    @Transactional
    public void heartbeat(UUID userId, String deviceId, String appVersion) {
        sessions.findFirstByUserIdAndDeviceIdAndRevokedAtIsNull(userId, deviceId).ifPresent(s -> {
            boolean newVersion = appVersion != null && !appVersion.isBlank() && !appVersion.equals(s.getAppVersion());
            boolean stale = s.getLastSeenAt() == null || s.getLastSeenAt().isBefore(Instant.now().minus(SEEN_EVERY));
            if (!newVersion && !stale) return;
            if (newVersion) s.setAppVersion(appVersion);
            s.setLastSeenAt(Instant.now());
            sessions.save(s);
        });
    }

    static final java.time.Duration SEEN_EVERY = java.time.Duration.ofMinutes(5);

    @Transactional
    public void release(UUID sessionId, UUID releasedById, String reason) {
        DeviceSession s = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "No such device session."));
        s.setRevokedAt(Instant.now());
        s.setRevokedById(releasedById);
        s.setRevokedReason(reason);
    }

    /** Every phone currently holding a login, newest first: the admin's "All devices" screen. */
    @Transactional(readOnly = true)
    public List<DeviceSession> allActive() {
        return sessions.findByRevokedAtIsNullOrderByLastSeenAtDesc();
    }

    @Transactional(readOnly = true)
    public List<DeviceSession> forUser(UUID userId) {
        return sessions.findByUserIdOrderByLastSeenAtDesc(userId);
    }
}
