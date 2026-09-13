package com.specskart.lens;

import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Staff-facing price list for lens options — this is what the Specskart POS app edits.
 *  No separate POS backend: the app authenticates the same way the web admin does and
 *  hits this same specskart-api service. */
@RestController
@RequestMapping("/api/admin/lens-pricing")
public class AdminLensPricingController {

    public record OptionView(UUID id, String code, String label, long priceMinor, boolean inStock) {}
    public record UpdateRequest(Long priceMinor, Boolean inStock) {}

    private final LensPricingOptionRepository options;

    public AdminLensPricingController(LensPricingOptionRepository options) {
        this.options = options;
    }

    @GetMapping
    public List<OptionView> list() {
        return options.findAllByOrderByCodeAsc().stream().map(AdminLensPricingController::view).toList();
    }

    @PatchMapping("/{id}")
    public OptionView update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
        LensPricingOption o = options.findById(id)
                .orElseThrow(() -> ApiException.notFound("OPTION_NOT_FOUND", "No such pricing option."));
        if (req.priceMinor() != null) {
            if (req.priceMinor() < 0) throw ApiException.badRequest("BAD_PRICE", "Price can't be negative.");
            o.setPriceMinor(req.priceMinor());
        }
        if (req.inStock() != null) o.setInStock(req.inStock());
        return view(options.save(o));
    }

    private static OptionView view(LensPricingOption o) {
        return new OptionView(o.getId(), o.getCode(), o.getLabel(), o.getPriceMinor(), o.isInStock());
    }
}
