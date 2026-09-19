package com.specskart.membership;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One purchase of "Specskart Care". Unpaid rows are abandoned checkouts and are ignored
 * everywhere — a membership only exists once {@link #getPaidAt()} is set.
 *
 * <p>The price and discount are copied onto the row at purchase rather than read from
 * {@link MembershipService} at use time, so raising the plan price later never silently
 * changes what someone already bought.
 */
@Entity
@Table(name = "memberships")
@Getter
@Setter
public class Membership extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;

    @Column(nullable = false)
    private long priceMinor;

    @Column(nullable = false)
    private String currency = "ZMW";

    /** Percentage off every lens order for the life of this membership. */
    @Column(nullable = false)
    private int discountPercent;

    private String paymentRef;
    private Instant paidAt;
    private Instant expiresAt;

    /** Paid for, and not yet run out. */
    public boolean active() {
        return paidAt != null && expiresAt != null && expiresAt.isAfter(Instant.now());
    }
}
