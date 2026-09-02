package com.specskart.lead;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "lead_notes")
@Getter
@Setter
public class LeadNote extends BaseEntity {
    @Column(nullable = false)
    private UUID leadId;
    @Column(nullable = false, length = 4000)
    private String body;
    private String authorEmail;
}
