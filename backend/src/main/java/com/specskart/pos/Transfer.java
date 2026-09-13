package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Stock moving from one shop to another. Recording it decrements the source immediately
 *  (TRANSFER_OUT) and credits the destination when marked received (TRANSFER_IN) — two
 *  separate movements, so stock isn't double-counted while it's physically in transit. */
@Entity
@Table(name = "transfers")
@Getter
@Setter
public class Transfer extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String reference;

    private UUID fromStoreId;
    private UUID toStoreId;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING | IN_TRANSIT | COMPLETED | CANCELLED

    @Column(nullable = false)
    private String notes = "";

    /** Set when this transfer was built by "passing on" stock that arrived on an earlier
     *  transfer — matches NG POS's own convention. */
    private UUID sourceTransferId;
}
