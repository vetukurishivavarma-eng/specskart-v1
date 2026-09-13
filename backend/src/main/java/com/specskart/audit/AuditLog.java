package com.specskart.audit;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog extends BaseEntity {

    private String entityType;
    private String entityId;
    private String action;
    private UUID actorId;
    private String actorName = "";
    private UUID storeId;
    private String summary = "";
}
