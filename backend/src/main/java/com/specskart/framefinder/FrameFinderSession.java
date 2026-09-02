package com.specskart.framefinder;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "frame_finder_sessions", indexes = @Index(name = "idx_ffs_token", columnList = "tokenHash", unique = true))
@Getter
@Setter
public class FrameFinderSession extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant completedAt;
    private Instant lastAccessedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrameFinderSessionStatus status = FrameFinderSessionStatus.CREATED;

    private UUID sourceCampaignId;

    public boolean isExpired() {
        return status == FrameFinderSessionStatus.EXPIRED || Instant.now().isAfter(expiresAt);
    }
}
