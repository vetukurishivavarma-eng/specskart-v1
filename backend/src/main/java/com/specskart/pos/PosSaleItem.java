package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "pos_sale_items")
@Getter
@Setter
public class PosSaleItem extends BaseEntity {

    @Column(nullable = false)
    private UUID saleId;

    private UUID productId;

    /** Denormalised so a historical receipt survives the product being renamed. */
    @Column(nullable = false)
    private String productName;
    @Column(nullable = false)
    private String sku = "";

    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private long unitPriceMinor;
    @Column(nullable = false)
    private long discountMinor;
    @Column(nullable = false)
    private long lineTotalMinor;
}
