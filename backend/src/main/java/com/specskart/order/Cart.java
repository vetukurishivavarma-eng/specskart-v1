package com.specskart.order;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Server-side cart keyed by an opaque token held in a cookie. May be linked to a lead. */
@Entity
@Table(name = "carts")
@Getter
@Setter
public class Cart extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String token;

    private UUID leadId;

    private String promoCode;

    /** set when we've sent the abandoned-cart nudge, so we only send it once */
    private Instant nudgedAt;

    /** set when this cart became an order */
    private Instant orderedAt;

    /** Prescription-lens choice for lensable frames in this cart. NON_PRESCRIPTION | SINGLE_VISION | PROGRESSIVE | BLUE_LIGHT */
    private String lensType;
    @Column(nullable = false)
    private long lensAddMinor = 0;
    @Column(columnDefinition = "text")
    private String rxJson;
}
