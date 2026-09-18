package com.specskart.lens;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Specskart POS: counter billing. Web orders (verified over WhatsApp, waiting for someone
 *  to hand over the lenses and take payment) and walk-ins (staff enter + bill on the spot)
 *  both land in the same lens_inquiries pipeline — see LensInquiryService. */
@RestController
@RequestMapping("/api/admin/lens-sales")
public class AdminLensSalesController {

    private final LensInquiryService service;

    public AdminLensSalesController(LensInquiryService service) {
        this.service = service;
    }

    /** Web orders ready for the counter to collect payment on. */
    @GetMapping("/pending")
    public List<LensDtos.SaleView> pending() {
        return service.pendingWebOrders();
    }

    @PostMapping("/{id}/complete")
    public LensDtos.InquiryView complete(@PathVariable UUID id, @RequestBody LensDtos.CompleteSale req) {
        return service.completeSale(id, req);
    }

    @PostMapping("/{id}/fulfilment")
    public LensDtos.InquiryView advance(@PathVariable UUID id, @RequestBody LensDtos.AdvanceFulfilment req) {
        return service.advanceFulfilment(id, req);
    }

    @PostMapping("/walk-in")
    public LensDtos.InquiryView walkIn(@RequestBody LensDtos.WalkInSale req) {
        return service.walkInSale(req);
    }

    @GetMapping
    public List<LensDtos.SaleView> onDay(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.salesOn(date);
    }

    @GetMapping("/summary")
    public LensDtos.DaySummary summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summaryOn(date);
    }
}
