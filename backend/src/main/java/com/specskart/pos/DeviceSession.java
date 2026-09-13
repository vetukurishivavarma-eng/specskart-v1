package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One till, one account, one device — a password can't run on four phones, and every sale
 *  is attributable to a device an administrator can identify and release. Deliberately not
 *  unique on (userId, deviceId): releasing a device keeps the row as history, so the same
 *  handset signing in again is a new claim, not a collision. Uniqueness that matters — one
 *  live claim per device — is enforced in {@link DeviceSessionService#claim}. */
@Entity
@Table(name = "device_sessions")
@Getter
@Setter
public class DeviceSession extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String deviceName = "";

    @Column(nullable = false)
    private String platform = "android";

    private String appVersion;

    @Column(nullable = false)
    private Instant lastSeenAt;

    private Instant revokedAt;
    private UUID revokedById;
    private String revokedReason;

    public boolean isActive() {
        return revokedAt == null;
    }
}
