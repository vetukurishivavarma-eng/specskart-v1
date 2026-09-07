package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
public class PromoCode extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String discountType = "PERCENT"; // PERCENT | FIXED

    /** percent 1..100, or a fixed amount in minor units */
    @Column(nullable = false)
    private int discountValue;

    @Column(nullable = false)
    private long minSubtotalMinor = 0;

    private Integer maxRedemptions;

    @Column(nullable = false)
    private int redeemedCount = 0;

    private Instant expiresAt;

    /** true = the funnel may hand this code to a lead automatically after face analysis. */
    @Column(nullable = false)
    private boolean autoIssue = false;

    @Column(nullable = false)
    private boolean active = true;

    public boolean usable(long subtotalMinor) {
        if (!active) return false;
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return false;
        if (maxRedemptions != null && redeemedCount >= maxRedemptions) return false;
        return subtotalMinor >= minSubtotalMinor;
    }

    public long discountFor(long subtotalMinor) {
        long d = "PERCENT".equals(discountType)
                ? Math.round(subtotalMinor * (discountValue / 100.0))
                : discountValue;
        return Math.min(d, subtotalMinor);
    }
}
