package com.specskart.lead;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One "scan this to join" code shown on a POS till. Verified the moment the customer's
 *  WhatsApp message carrying the code arrives. */
@Entity
@Table(name = "walk_in_verifications")
@Getter
@Setter
public class WalkInVerification extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    private UUID storeId;
    private UUID staffUserId;
    private String customerName;
    private UUID leadId;
    private Instant verifiedAt;
}
