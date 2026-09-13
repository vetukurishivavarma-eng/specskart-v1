package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** How much stock one store is carrying of one product. Absent means zero — a row is only
 *  created the first time a store's stock is set or moved. */
@Entity
@Table(name = "product_inventory")
@Getter
@Setter
public class ProductInventory extends BaseEntity {

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity = 0;

    @Column(nullable = false)
    private int reorderLevel = 3;
}
