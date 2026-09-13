package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A till sale (or its reversal). Line items and payments are relational, not a JSON blob,
 *  so reporting is plain SQL. {@code clientReference} is a client-supplied idempotency key —
 *  a queued offline sale replayed after a lost response is a no-op, not a duplicate charge. */
@Entity
@Table(name = "pos_sales")
@Getter
@Setter
public class PosSale extends BaseEntity {

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false, unique = true)
    private String receiptNumber;

    @Column(nullable = false)
    private String saleType = "SALE"; // SALE | REFUND

    @Column(nullable = false)
    private String status = "COMPLETED"; // COMPLETED | VOIDED

    @Column(nullable = false)
    private long subtotalMinor;
    @Column(nullable = false)
    private long discountMinor;
    @Column(nullable = false)
    private long totalMinor;

    private UUID cashierId;
    @Column(nullable = false)
    private String cashierName = "";

    private String customerName;
    private String customerPhone;

    @Column(nullable = false)
    private String notes = "";

    @Column(unique = true)
    private String clientReference;

    private UUID reversesId;
    private Instant voidedAt;
    private String voidReason;

    public boolean isVoided() {
        return voidedAt != null;
    }
}
