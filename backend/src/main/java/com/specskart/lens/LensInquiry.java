package com.specskart.lens;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A lens-only prescription inquiry — the client's parallel funnel to frame shopping
 * (currently the only one shown anywhere: frames stay in the code, just not surfaced).
 * Created the moment a phone number is submitted; most fields fill in as the shopper
 * gets through the form, gated on {@link #getPhoneVerifiedAt()}.
 */
@Entity
@Table(name = "lens_inquiries")
@Getter
@Setter
public class LensInquiry extends BaseEntity {

    private UUID leadId;

    @Column(nullable = false)
    private String verifyTokenHash;
    @Column(nullable = false)
    private String phoneRaw;
    private String waId;
    private Instant phoneVerifiedAt;

    /** CLEAR | PHOTOCHROMATIC */
    private String lensType;
    @Column(nullable = false)
    private boolean blueBlock = false;

    private String customerName;
    private Integer age;
    private String gender;

    private BigDecimal sphRight;
    private BigDecimal sphLeft;
    private BigDecimal cylRight;
    private BigDecimal cylLeft;
    private Integer axisRight;
    private Integer axisLeft;
    private BigDecimal addPower;
    /** BIFOCAL | PROGRESSIVE — only meaningful when addPower > 0 */
    private String lensStructure;
    /** Axis far from the 90°/180° "normal" bands — the client flagged these as needing a
     *  special (non-stock) lens, so route the lead to staff rather than auto-price it. */
    @Column(nullable = false)
    private boolean specialAxis = false;

    private Long priceMinor;
    @Column(nullable = false)
    private String currency = "ZMW";
    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT | VERIFIED | PRICED | SUBMITTED | SOLD
    private Instant nudgedAt;

    /** True for a counter sale entered directly by staff in the Specskart POS app —
     *  no WhatsApp verification step, the staff member vouches for the customer in person. */
    @Column(nullable = false)
    private boolean walkIn = false;
    /** CASH | CARD | MOBILE — set when {@link #status} reaches SOLD. */
    private String paymentMethod;
    private String soldBy;
    private String shopName;

    /** Where the finished lens is delivered. Captured on the /lens page after the quote and
     *  required before submit — a web order with no address is one the lab can't fulfil.
     *  Null for a walk-in: the customer is standing at the counter. */
    private String deliveryName;
    @Column(length = 1000)
    private String deliveryAddress;
    private String deliveryArea;
    private String deliveryLandmark;

    /** Idempotency key for a walk-in sale from the Specskart POS app's offline queue — a
     *  retry after a dropped connection can't double-sell. Null for web-originated inquiries. */
    @Column(unique = true)
    private String clientReference;

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }
}
