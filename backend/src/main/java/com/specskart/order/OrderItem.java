package com.specskart.order;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    private UUID productId;

    @Column(nullable = false)
    private String productName;
    private String productSlug;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long unitPriceMinor;

    public long lineTotalMinor() {
        return unitPriceMinor * qty;
    }
}
