package com.specskart.pos;

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

    public AdminSaleController(SaleService sales, CurrentUser currentUser) {
        this.sales = sales;
        this.currentUser = currentUser;
    }

    @PostMapping
    public PosDtos.SaleView create(@RequestBody PosDtos.CreateSale req, Authentication auth) {
        return sales.createSale(req, currentUser.idOf(auth), currentUser.nameOf(auth));
    }

    @GetMapping("/{id}")
    public PosDtos.SaleView get(@PathVariable UUID id) {
        return sales.getSale(id);
    }

    @PostMapping("/{id}/void")
    public PosDtos.SaleView void_(@PathVariable UUID id, @RequestBody PosDtos.VoidSale req, Authentication auth) {
        return sales.voidSale(id, req.reason(), currentUser.idOf(auth));
    }

    @PostMapping("/{id}/refund")
    public PosDtos.SaleView refund(@PathVariable UUID id, @RequestBody RefundRequest req, Authentication auth) {
        return sales.refund(id, req.items(), req.method(), req.reason(), currentUser.idOf(auth));
    }

    /** {@code date} for a single day (used by the till's own day view); {@code from}/{@code to}
     *  for a range (the Sales History screen). */
    @GetMapping
    public List<PosDtos.SaleView> forStore(@RequestParam UUID storeId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate rangeFrom = from != null ? from : (date != null ? date : LocalDate.now());
        LocalDate rangeTo = to != null ? to : rangeFrom;
        var fromInstant = rangeFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        var toInstant = rangeTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return sales.salesForStore(storeId, fromInstant, toInstant);
    }
}
