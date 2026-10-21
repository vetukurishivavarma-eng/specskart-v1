package com.specskart.spin;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A customer's one spin. lead_id is UNIQUE: the database itself refuses a second spin. */
@Entity
@Table(name = "spins")
@Getter
@Setter
public class Spin extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID leadId;

    @Column(nullable = false, length = 80)
    private String prizeLabel;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant redeemedAt;
    private String redeemedBy;
}
