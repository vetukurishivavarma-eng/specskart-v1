package com.specskart.recommendation;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "frame_categories")
@Getter
@Setter
public class FrameCategory extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String displayName;
    @Column(length = 2000)
    private String description;
    private String imageUrl;
    private boolean active = true;
}
