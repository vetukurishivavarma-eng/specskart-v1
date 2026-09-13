package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Every stock change anybody made, and why — a running audit trail alongside the plain
 *  quantity on {@link ProductInventory}. */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement extends BaseEntity {

    @Column(nullable = false)
    private UUID storeId;
    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String movementType; // PURCHASE | SALE | ADJUSTMENT | TRANSFER_IN | TRANSFER_OUT | REFUND

    /** Signed: negative for sales and transfers out. */
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private int balance;

    private String reference;
    private String note;
    private UUID userId;
}
