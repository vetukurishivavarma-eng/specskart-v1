package com.specskart.order;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
public class CartItem extends BaseEntity {

    @Column(nullable = false)
    private UUID cartId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long unitPriceMinor;

    /** Stock is reserved until this moment; past it the units are released back. */
    private Instant heldUntil;

    public long lineTotalMinor() {
        return unitPriceMinor * qty;
    }
}
