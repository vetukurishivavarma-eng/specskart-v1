package com.specskart.whatsapp;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Idempotency ledger for inbound webhook events. */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
public class WebhookEvent extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String dedupeKey;

    private String kind;
}
