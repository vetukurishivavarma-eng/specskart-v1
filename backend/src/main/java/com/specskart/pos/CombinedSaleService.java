package com.specskart.pos;

import com.specskart.lens.LensDtos;
import com.specskart.lens.LensInquiryService;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** One counter bill for a frame and its lenses. Both halves run in ONE transaction: if the
 *  lens half is rejected (off-sheet Rx, bad discount) the frame sale and its stock movement
 *  roll back too, so staff never end up with half a bill. Each half still dedupes on its own
 *  clientReference, so an offline replay of the whole bill is a no-op. */
@Service
public class CombinedSaleService {

    public record CombinedSale(PosDtos.CreateSale frame, LensDtos.WalkInSale lens) {}
    public record CombinedView(PosDtos.SaleView frame, LensDtos.InquiryView lens, long totalMinor) {}

    private final SaleService sales;
    private final LensInquiryService lenses;
    private final PosSaleRepository posSales;

    public CombinedSaleService(SaleService sales, LensInquiryService lenses, PosSaleRepository posSales) {
        this.sales = sales;
        this.lenses = lenses;
        this.posSales = posSales;
    }

    @Transactional
    public CombinedView create(CombinedSale req, UUID cashierId, String cashierName) {
        boolean hasFrame = req.frame() != null && req.frame().items() != null && !req.frame().items().isEmpty();
        boolean hasLens = req.lens() != null;
        if (!hasFrame && !hasLens) throw ApiException.badRequest("EMPTY_SALE", "Add a frame or lenses first.");

        // Frame first: the lens half sends the customer a WhatsApp as its very last step, so
        // nothing goes out unless everything before it succeeded.
        PosDtos.SaleView frame = hasFrame ? sales.createSale(req.frame(), cashierId, cashierName) : null;
        LensDtos.InquiryView lens = hasLens ? lenses.walkInSale(req.lens()) : null;

        if (frame != null && lens != null) {
            String link = "With lenses LENS-" + lens.id().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
            posSales.findById(frame.id()).ifPresent(s -> { s.setNotes(link); posSales.save(s); });
        }
        long total = (frame == null ? 0 : frame.totalMinor()) + (lens == null || lens.priceMinor() == null ? 0 : lens.priceMinor());
        return new CombinedView(frame, lens, total);
    }
}
