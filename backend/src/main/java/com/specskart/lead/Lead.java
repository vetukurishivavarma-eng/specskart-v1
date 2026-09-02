package com.specskart.lead;

import com.specskart.campaign.UtmData;
import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "leads", indexes = {
        @Index(name = "idx_lead_wa", columnList = "whatsappNumber"),
        @Index(name = "idx_lead_status", columnList = "status")
})
@Getter
@Setter
public class Lead extends BaseEntity {

    @Column(unique = true)
    private String whatsappWaId;

    private String whatsappNumber;
    private String countryCode;
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcquisitionSource acquisitionSource = AcquisitionSource.UNKNOWN;

    private UUID campaignId;
    private String adId;
    private String adSetId;
    private String clickId;
    private String referrer;
    private String landingPage;

    @Embedded
    private UtmData utm = UtmData.empty();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> providerMetadata = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status = LeadStatus.NEW;

    private Instant firstContactAt;
    private Instant lastContactAt;

    private UUID assignedToUserId;

    /** Face analysis denormalized snapshot for fast CRM listing. */
    private String faceShape;
    private Double faceConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private java.util.List<String> recommendedFrameCategories = new java.util.ArrayList<>();
}
