package com.specskart.recommendation;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "face_shape_frame_recommendations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"faceShapeId", "frameCategoryId"}))
@Getter
@Setter
public class FaceShapeFrameRecommendation extends BaseEntity {

    @Column(nullable = false)
    private UUID faceShapeId;

    @Column(nullable = false)
    private UUID frameCategoryId;

    /** POSITIVE = recommend, CAUTION = use carefully / avoid. */
    @Column(nullable = false)
    private String stance = "POSITIVE";

    @Column(nullable = false)
    private int priority = 100;

    @Column(length = 1000)
    private String recommendationReason;

    private boolean genderNeutral = true;
    private boolean active = true;

    @Column(length = 1000)
    private String notes;
}
