package com.specskart.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "consent_records", indexes = @Index(name = "idx_consent_lead", columnList = "leadId"))
@Getter
@Setter
public class ConsentRecord extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;

    /** CAMERA, PHOTO_PROCESSING, MARKETING, WHATSAPP */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status; // GRANTED | DENIED | WITHDRAWN

    @Column(nullable = false)
    private String source; // FRAME_FINDER | WHATSAPP | WEBSITE

    private String policyVersion;
}
