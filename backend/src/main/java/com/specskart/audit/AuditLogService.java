package com.specskart.audit;

import org.springframework.stereotype.Service;

import java.util.UUID;

/** Written at the mutation points that matter for accountability -- not a generic
 *  interceptor over every save(). See the call sites for the exact coverage: sales
 *  (create/void/refund), stock (adjust/bulk import), store price overrides, purchase
 *  invoices (create/pay), transfers (create/receive/cancel), staff and shop CRUD. */
@Service
public class AuditLogService {

    private final AuditLogRepository logs;

    public AuditLogService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public void record(String entityType, String entityId, String action,
                        UUID actorId, String actorName, UUID storeId, String summary) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setActorId(actorId);
        log.setActorName(actorName == null ? "" : actorName);
        log.setStoreId(storeId);
        log.setSummary(summary == null ? "" : summary);
        logs.save(log);
    }
}
