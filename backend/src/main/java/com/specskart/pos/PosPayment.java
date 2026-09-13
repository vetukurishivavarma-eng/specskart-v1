package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "pos_payments")
@Getter
@Setter
public class PosPayment extends BaseEntity {

    @Column(nullable = false)
    private UUID saleId;

    @Column(nullable = false)
    private String method; // CASH | CARD | MOBILE

    @Column(nullable = false)
    private long amountMinor;

    private String reference;
}
