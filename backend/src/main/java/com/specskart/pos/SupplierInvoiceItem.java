package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "supplier_invoice_items")
@Getter
@Setter
public class SupplierInvoiceItem extends BaseEntity {

    @Column(nullable = false)
    private UUID invoiceId;
    private UUID productId;

    @Column(nullable = false)
    private String productName;
    @Column(nullable = false)
    private String sku = "";

    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private long unitCostMinor;
    @Column(nullable = false)
    private long lineTotalMinor;
}
