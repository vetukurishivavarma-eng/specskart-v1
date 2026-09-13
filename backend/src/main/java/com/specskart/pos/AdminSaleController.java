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

    @PostMapping("/{id}/void")
    public PosDtos.SaleView void_(@PathVariable UUID id, @RequestBody PosDtos.VoidSale req, Authentication auth) {
        return sales.voidSale(id, req.reason(), currentUser.idOf(auth));
    }

    @GetMapping
    public List<PosDtos.SaleView> onDay(@RequestParam UUID storeId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = from.plus(java.time.Duration.ofDays(1));
        return sales.salesForStore(storeId, from, to);
    }
}
