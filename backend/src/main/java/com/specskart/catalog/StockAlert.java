package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** "Tell me when this frame is available." One row per (product, WhatsApp number). */
@Entity
@Table(name = "stock_alerts", uniqueConstraints = @UniqueConstraint(name = "uq_stock_alert", columnNames = {"product_id", "wa_id"}))
@Getter
@Setter
public class StockAlert extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "wa_id", nullable = false)
    private String waId;

    private Instant notifiedAt;
}
