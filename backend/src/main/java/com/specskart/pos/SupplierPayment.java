package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One instalment against an invoice — part-payments are the normal case, so each is its own
 *  row with its own reference and the running balance is total - sum. */
@Entity
@Table(name = "supplier_payments")
@Getter
@Setter
public class SupplierPayment extends BaseEntity {

    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String method = "CASH"; // CASH | BANK_TRANSFER | MOBILE | CHEQUE | CARD | OTHER

    @Column(nullable = false)
    private String reference = "";
    @Column(nullable = false)
    private String note = "";

    @Column(nullable = false)
    private Instant paidAt;

    private UUID userId;
    @Column(nullable = false)
    private String userName = "";
}
