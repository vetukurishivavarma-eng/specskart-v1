package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Per-store price override. Absent means the product's own priceMinor applies everywhere. */
@Entity
@Table(name = "product_store_prices")
@Getter
@Setter
public class ProductStorePrice extends BaseEntity {

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long priceMinor;
}
