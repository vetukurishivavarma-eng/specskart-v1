package com.specskart.spin;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A customer's spin. One at a time: they can spin again once this prize's week is over (SpinService). */
@Entity
@Table(name = "spins")
@Getter
@Setter
public class Spin extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;

    @Column(nullable = false, length = 80)
    private String prizeLabel;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant redeemedAt;
    private String redeemedBy;

    /** Bring-a-friend prize: the new customer who messaged us with this code, and their own code. */
    private UUID friendLeadId;

    @Column(unique = true, length = 20)
    private String friendCode;

    private Instant friendRedeemedAt;
    private String friendRedeemedBy;

    /** Redeeming at the till: a WhatsApp'd verification code (hashed) for one of this spin's prize codes. */
    @Column(length = 20)
    private String otpCode;
    @Column(length = 64)
    private String otpHash;
    private Instant otpExpiresAt;
    @Column(nullable = false)
    private int otpTries;
    /** Rate limit on sending codes: when the last went out, and how many this prize has had. */
    private Instant otpSentAt;
    @Column(nullable = false)
    private int otpSends;
}
