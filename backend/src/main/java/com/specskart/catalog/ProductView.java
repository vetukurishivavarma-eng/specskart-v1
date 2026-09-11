package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One lead's most recent look at one product — upserted on every view. Feeds the
 *  abandoned-browse recovery job. */
@Entity
@Table(name = "product_views")
@Getter
@Setter
public class ProductView extends BaseEntity {
    private UUID leadId;
    private UUID productId;
    private Instant viewedAt;
    private Instant notifiedAt;
}
