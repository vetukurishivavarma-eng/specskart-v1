package com.specskart.audit;

import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Who changed what, and when — admin-only (a shop-scoped login sees its own shop's data
 *  live in the app already; the audit trail across shops is an owner's concern). */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AdminAuditLogController {

    public record AuditEntry(UUID id, String entityType, String entityId, String action,
                             String actorName, UUID storeId, String summary, Instant createdAt) {}

    private final AuditLogRepository logs;
    private final CurrentUser currentUser;

    public AdminAuditLogController(AuditLogRepository logs, CurrentUser currentUser) {
        this.logs = logs;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AuditEntry> list(@RequestParam(required = false) UUID storeId,
                                 @RequestParam(required = false) String entityType,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(defaultValue = "200") int limit,
                                 Authentication auth) {
        if (!currentUser.isAdmin(auth)) throw ApiException.forbidden("ADMIN_ONLY", "Admin only.");
        LocalDate rangeFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate rangeTo = to != null ? to : LocalDate.now();
        Instant fromInstant = rangeFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = rangeTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return logs.search(storeId, entityType, fromInstant, toInstant, PageRequest.of(0, Math.min(limit, 500))).stream()
                .map(a -> new AuditEntry(a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(),
                        a.getActorName(), a.getStoreId(), a.getSummary(), a.getCreatedAt()))
                .toList();
    }
}
