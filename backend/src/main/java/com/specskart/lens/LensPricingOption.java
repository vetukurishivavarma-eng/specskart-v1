package com.specskart.lens;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One editable line in the lens price list — CLEAR, PHOTOCHROMATIC, BLUE_BLOCK, BIFOCAL,
 *  PROGRESSIVE — set from the Specskart POS app (or admin) instead of hardcoded in
 *  {@link LensPricing}. {@code inStock=false} lets staff pull an option from sale (e.g. out
 *  of a coating) without deleting its price history. */
@Getter
@Setter
@Entity
@Table(name = "lens_pricing_options")
public class LensPricingOption extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock = true;
}
