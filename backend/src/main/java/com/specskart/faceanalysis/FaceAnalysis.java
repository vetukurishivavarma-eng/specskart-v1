package com.specskart.faceanalysis;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "face_analyses", indexes = @Index(name = "idx_face_lead", columnList = "leadId"))
@Getter
@Setter
public class FaceAnalysis extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;

    private UUID sessionId;

    @Column(nullable = false)
    private String predictedFaceShape;

    @Column(nullable = false)
    private double confidenceScore;

    @Column(length = 2000)
    private String recommendationSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private List<String> recommendedFrameCategories = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> geometryData = new HashMap<>();

    private String imageStorageReference;

    @Column(nullable = false)
    private boolean consentGiven;

    @Column(nullable = false)
    private String analysisVersion = "v1";
}
