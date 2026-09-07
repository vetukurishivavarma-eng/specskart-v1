package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A sellable frame. Money is stored in minor units (ngwee) as a long. */
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(length = 4000)
    private String description;

    /** Frame-category code (see frame_categories). Drives face-shape matching via the recommendation rules. */
    private String frameCategoryCode;

    private String material;
    private String colour;

    @Column(nullable = false)
    private String gender = "UNISEX"; // UNISEX | MEN | WOMEN

    @Column(nullable = false)
    private long priceMinor;

    private Long compareAtMinor;

    @Column(nullable = false)
    private String currency = "ZMW";

    @Column(nullable = false)
    private int stockQty = 0;

    @Column(nullable = false)
    private boolean lensable = false;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT | ACTIVE | ARCHIVED

    @Column(nullable = false)
    private boolean featured = false;

    /** Serving path of a transparent-PNG render used for virtual try-on; null if none. */
    private String tryOnImageUrl;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean inStock() {
        return stockQty > 0;
    }
}
