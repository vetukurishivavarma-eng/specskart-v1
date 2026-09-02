package com.specskart.recommendation;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "face_shapes")
@Getter
@Setter
public class FaceShape extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String code; // OVAL, ROUND, SQUARE, RECTANGLE, HEART, DIAMOND, TRIANGLE
    @Column(nullable = false)
    private String displayName;
    @Column(length = 2000)
    private String description;
    private boolean active = true;
}
