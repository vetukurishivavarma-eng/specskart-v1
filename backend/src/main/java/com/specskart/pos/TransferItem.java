package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "transfer_items")
@Getter
@Setter
public class TransferItem extends BaseEntity {

    @Column(nullable = false)
    private UUID transferId;
    @Column(nullable = false)
    private UUID productId;
    @Column(nullable = false)
    private int quantity;
}
