package com.specskart.analytics;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "lead_events", indexes = {
        @Index(name = "idx_event_lead", columnList = "leadId"),
        @Index(name = "idx_event_type", columnList = "eventType")
})
@Getter
@Setter
public class LeadEvent extends BaseEntity {

    private UUID leadId;
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadEventType eventType;

    private String source;
    private UUID campaignId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> metadata = new HashMap<>();
}
