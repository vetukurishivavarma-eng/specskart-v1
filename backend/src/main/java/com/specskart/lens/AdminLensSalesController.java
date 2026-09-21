package com.specskart.lens;

import com.specskart.shared.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
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
    private final CurrentUser currentUser;

    public AdminLensSalesController(LensInquiryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** The doorstep queue. `delivered=true` returns what has already been handed over
     *  instead, which is the app's "Delivered" tab. */
    @GetMapping("/pending")
    public List<LensDtos.SaleView> pending(
            @RequestParam(name = "delivered", defaultValue = "false") boolean delivered) {
        return service.pendingWebOrders(delivered);
    }

    @PostMapping("/{id}/complete")
    public LensDtos.InquiryView complete(@PathVariable UUID id, @RequestBody LensDtos.CompleteSale req) {
        return service.completeSale(id, req);
    }

    @PostMapping("/{id}/fulfilment")
    public LensDtos.InquiryView advance(@PathVariable UUID id, @RequestBody LensDtos.AdvanceFulfilment req) {
        return service.advanceFulfilment(id, req);
    }

    @PostMapping("/{id}/cancel")
    public LensDtos.InquiryView cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

        @PostMapping("/walk-in")
    public LensDtos.InquiryView walkIn(@RequestBody LensDtos.WalkInSale req, Authentication auth) {
        // the pair of lens blanks comes off this shop's shelf
        if (req.storeId() != null) currentUser.assertStoreAccess(auth, req.storeId());
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
