package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Serialises receipt numbering per store per day: {@code STORECODE-YYYYMMDD-NNNNNN} must
 *  never collide even when several tills post at the same moment. */
@Entity
@Table(name = "receipt_counters")
@Getter
@Setter
public class ReceiptCounter extends BaseEntity {

    @Column(nullable = false)
    private UUID storeId;

    /** Local date as YYYYMMDD. */
    @Column(nullable = false)
    private String saleDay;

    @Column(nullable = false)
    private int sequence = 0;
}
