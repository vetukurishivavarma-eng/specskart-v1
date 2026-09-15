package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A sellable frame. Money is stored in minor units (ngwee) as a long.
 *  DynamicUpdate: stockQty is moved by atomic UPDATE queries (cart holds, POS mirror) while a
 *  managed copy may be open elsewhere in the same transaction — only dirty columns get written,
 *  so saving that copy for an unrelated change can't put a stale stock count back. */
@org.hibernate.annotations.DynamicUpdate
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

    /** POS-facing fields — nullable, since online-only products never need them.
     *  Per-store stock/price overrides live in {@link com.specskart.pos.ProductInventory}/
     *  {@link com.specskart.pos.ProductStorePrice}; this stockQty/priceMinor stay the
     *  online-store defaults. */
    @Column(unique = true)
    private String sku;
    private String barcode;
    /** Landed cost, for profit analytics — never shown to shoppers. */
    @Column(nullable = false)
    private long costPriceMinor = 0;

    @Column(nullable = false)
    private boolean lensable = false;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT | ACTIVE | ARCHIVED

    @Column(nullable = false)
    private boolean featured = false;

    /** If set and in the future, the product is a scheduled drop — hidden from the store until then. */
    private java.time.Instant dropsAt;

    @Column(nullable = false)
    private boolean limitedEdition = false;

    /** Serving path of a transparent-PNG render used for virtual try-on; null if none. */
    private String tryOnImageUrl;

    /** FRAME (default) or ACCESSORY (case, cleaning kit, chain…) — drives "complete the look". */
    @Column(nullable = false)
    private String kind = "FRAME";

    public boolean isAccessory() {
        return "ACCESSORY".equals(kind);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean inStock() {
        return stockQty > 0;
    }

    /** True while a scheduled drop is still counting down. */
    public boolean isUpcoming() {
        return dropsAt != null && dropsAt.isAfter(java.time.Instant.now());
    }
}
