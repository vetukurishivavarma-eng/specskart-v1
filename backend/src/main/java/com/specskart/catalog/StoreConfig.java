package com.specskart.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Single-row storefront configuration, editable from the admin portal. */
@Entity
@Table(name = "store_config")
@Getter
@Setter
public class StoreConfig {

    @Id
    private int id = 1;

    private String heroTitle = "Frames matched to your face.";
    private String heroSubtitle = "Take the 30-second face analysis on WhatsApp, then shop the styles made for your shape.";
    @Column(length = 1024)
    private String heroImageUrl;

    @Column(nullable = false)
    private long shippingFeeMinor = 0;

    private Long freeShippingOverMinor;

    @Column(nullable = false)
    private String deliveryEta = "2–4 working days";

    @Column(nullable = false)
    private String currency = "ZMW";

    /** Waive delivery on a customer's first order (acquisition hook). */
    @Column(nullable = false)
    private boolean firstOrderFreeShipping = false;

    @Column(nullable = false, length = 300)
    private String paymentNote = "Pay by card, MTN / Airtel Money, or cash on delivery.";

    @Column(nullable = false, length = 300)
    private String guaranteeNote = "Delivered across Zambia · 30-day fit guarantee · free frame adjustments.";

    public long shippingFor(long subtotalMinor) {
        if (freeShippingOverMinor != null && subtotalMinor >= freeShippingOverMinor) return 0;
        return shippingFeeMinor;
    }
}
