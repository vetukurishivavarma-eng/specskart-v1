package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** A delivery from a supplier, priced. Posting one puts stock on the shelf (stock_movements
 *  PURCHASE rows) and records the money owed, in the same transaction — see PurchaseService. */
@Entity
@Table(name = "supplier_invoices")
@Getter
@Setter
public class SupplierInvoice extends BaseEntity {

    @Column(nullable = false)
    private UUID supplierId;
    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private String invoiceNumber;
    @Column(nullable = false)
    private LocalDate invoiceDate;
    private LocalDate dueDate;

    @Column(nullable = false)
    private long subtotalMinor;
    @Column(nullable = false)
    private long otherChargesMinor;
    @Column(nullable = false)
    private long totalMinor;
    @Column(nullable = false)
    private long amountPaidMinor;

    @Column(nullable = false)
    private String status = "UNPAID"; // UNPAID | PARTIAL | PAID

    @Column(nullable = false)
    private String notes = "";

    private UUID createdById;
    @Column(nullable = false)
    private String createdByName = "";

    public long balanceMinor() {
        return totalMinor - amountPaidMinor;
    }
}
