package com.specskart.order;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String orderNo;

    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false)
    private String customerName;
    @Column(nullable = false)
    private String customerPhone;
    private String customerEmail;

    @Column(nullable = false, length = 1000)
    private String shipAddress;
    @Column(nullable = false)
    private String shipCity;

    @Column(nullable = false)
    private long subtotalMinor;
    @Column(nullable = false)
    private long discountMinor = 0;
    @Column(nullable = false)
    private long shippingMinor = 0;
    @Column(nullable = false)
    private long totalMinor;

    @Column(nullable = false)
    private String currency = "ZMW";

    private String promoCode;
    private String paymentProvider;
    private String paymentRef;
    private Instant paidAt;

    /** When the post-purchase "thanks + come back" WhatsApp went out. */
    private Instant followedUpAt;

    private UUID referredByLeadId;
    @Column(nullable = false)
    private int pointsEarned = 0;
    @Column(nullable = false)
    private int pointsRedeemed = 0;
    @Column(nullable = false)
    private boolean referralCredited = false;

    private String lensType;
    @Column(nullable = false)
    private long lensAddMinor = 0;
    @Column(columnDefinition = "text")
    private String rxJson;
}
