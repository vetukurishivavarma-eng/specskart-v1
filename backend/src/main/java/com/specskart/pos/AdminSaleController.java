package com.specskart.pos;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/sales")
public class AdminSaleController {

    public record RefundRequest(List<PosDtos.SaleItemRequest> items, String method, String reason) {}

    private final SaleService sales;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminSaleController(SaleService sales, CurrentUser currentUser, AuditLogService audit) {
        this.sales = sales;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping
    public PosDtos.SaleView create(@RequestBody PosDtos.CreateSale req, Authentication auth) {
        currentUser.assertStoreAccess(auth, req.storeId());
        PosDtos.SaleView sale = sales.createSale(req, currentUser.idOf(auth), currentUser.nameOf(auth));
        audit.record("SALE", sale.id().toString(), "CREATE", currentUser.idOf(auth), currentUser.nameOf(auth),
                sale.storeId(), sale.receiptNumber() + " · " + sale.totalMinor());
        return sale;
    }

    @GetMapping("/{id}")
    public PosDtos.SaleView get(@PathVariable UUID id, Authentication auth) {
        PosDtos.SaleView sale = sales.getSale(id);
        currentUser.assertStoreAccess(auth, sale.storeId());
        return sale;
    }

    @PostMapping("/{id}/void")
    public PosDtos.SaleView void_(@PathVariable UUID id, @RequestBody PosDtos.VoidSale req, Authentication auth) {
        currentUser.assertStoreAccess(auth, sales.getSale(id).storeId());
        PosDtos.SaleView sale = sales.voidSale(id, req.reason(), currentUser.idOf(auth));
        audit.record("SALE", id.toString(), "VOID", currentUser.idOf(auth), currentUser.nameOf(auth),
                sale.storeId(), req.reason());
        return sale;
    }

    @PostMapping("/{id}/refund")
    public PosDtos.SaleView refund(@PathVariable UUID id, @RequestBody RefundRequest req, Authentication auth) {
        currentUser.assertStoreAccess(auth, sales.getSale(id).storeId());
        PosDtos.SaleView refund = sales.refund(id, req.items(), req.method(), req.reason(), currentUser.idOf(auth));
        audit.record("SALE", id.toString(), "REFUND", currentUser.idOf(auth), currentUser.nameOf(auth),
                refund.storeId(), refund.receiptNumber() + " · " + refund.totalMinor());
        return refund;
    }

    /** {@code date} for a single day (used by the till's own day view); {@code from}/{@code to}
     *  for a range (the Sales History screen). */
    @GetMapping
    public List<PosDtos.SaleView> forStore(@RequestParam UUID storeId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        LocalDate rangeFrom = from != null ? from : (date != null ? date : LocalDate.now());
        LocalDate rangeTo = to != null ? to : rangeFrom;
        var fromInstant = rangeFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        var toInstant = rangeTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return sales.salesForStore(storeId, fromInstant, toInstant);
    }
}
