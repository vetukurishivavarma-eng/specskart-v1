package com.specskart.whatsapp;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One staff alert to one staff number, kept until Meta confirms it reached the phone. */
@Entity
@Table(name = "staff_alerts")
@Getter
@Setter
public class StaffAlert extends BaseEntity {

    public enum Status { PENDING, SENT, DELIVERED, GAVE_UP }

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false)
    private UUID refId;

    @Column(nullable = false, length = 40)
    private String refNo;

    @Column(nullable = false, length = 32)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private int attempts;

    @Column(length = 16)
    private String lastPath;

    @Column(length = 128)
    private String wamid;

    @Column(length = 500)
    private String lastError;

    private Instant nextAttemptAt;

    @Column(nullable = false, length = 8000)
    private String body;

    @Column(length = 1000)
    private String pdfUrl;

    @Column(length = 120)
    private String pdfName;

    /** Template body params joined by \u001F (they are single-line by the time Meta sees them). */
    @Column(length = 2000)
    private String templateParams;

    @Column(length = 1000)
    private String rxUrl;

    @Column(length = 120)
    private String rxName;

    private boolean rxImage;
}
