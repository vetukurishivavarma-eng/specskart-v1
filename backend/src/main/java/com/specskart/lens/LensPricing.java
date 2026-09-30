package com.specskart.lens;

import com.specskart.membership.MembershipService;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The client's price list (V51): one row per lens category x SPH band, plus a flat surcharge
 * for CYL ±2.25–±4.00 and RX rows for bifocal/progressive that stock lenses can't cover.
 * Every price is staff-editable via {@code /api/admin/lens-pricing} (Specskart POS -> Lens
 * pricing); only the band boundaries below are fixed, because they're printed on the list.
 */
@Service
class LensPricing {

    /** Same values V51 seeds. Only used when a row is missing — the dev/test profiles build the
     *  schema without Flyway — so a gap never breaks quoting. */
    private static final Map<String, Long> DEFAULTS = Map.ofEntries(
            Map.entry("SV_CLEAR", 32_000L), Map.entry("SV_CLEAR_BB", 36_000L),
            Map.entry("PG_4", 45_000L), Map.entry("PG_8", 63_000L), Map.entry("PG_10", 99_900L),
            Map.entry("PG_BB_4", 63_000L), Map.entry("PG_BB_8", 99_900L), Map.entry("PG_BB_10", 117_000L),
            Map.entry("BF_2", 63_000L), Map.entry("BF_3", 81_000L),
            Map.entry("BF_BB_2", 95_000L), Map.entry("BF_BB_3", 131_000L),
            Map.entry("PROG", 113_000L), Map.entry("PROG_BB_2", 135_000L), Map.entry("PROG_BB_3", 153_000L),
            Map.entry("CYL_EXTRA", 20_000L),
            Map.entry("RX_BF", 150_000L), Map.entry("RX_BF_BB", 180_000L),
            Map.entry("RX_PROG", 230_000L), Map.entry("RX_PROG_BB", 270_000L));

    private final LensPricingOptionRepository options;
    private final MembershipService memberships;

    LensPricing(LensPricingOptionRepository options, MembershipService memberships) {
        this.options = options;
        this.memberships = memberships;
    }

    /**
     * Every quote in the funnel comes through here — the form, the resume link, the counter and
     * the gateway — so a member's discount lands on all of them without any other caller needing
     * to know memberships exist.
     */
    long quote(LensInquiry q) {
        return memberships.applyDiscount(quoteBeforeDiscount(q), q.getLeadId());
    }

    private long quoteBeforeDiscount(LensInquiry q) {
        double sph = Math.max(abs(q.getSphRight()), abs(q.getSphLeft()));
        double cyl = Math.max(abs(q.getCylRight()), abs(q.getCylLeft()));
        double add = abs(q.getAddPower());
        boolean bb = q.isBlueBlock();
        if (cyl > 4) throw outOfRange();

        if (add > 0) {
            boolean prog = "PROGRESSIVE".equals(q.getLensStructure());
            // ponytail: the list only has Colormatic (PG) multifocals, so a "clear" bifocal/progressive
            // is priced as Colormatic. Stock multifocals stop at SPH ±3 / CYL ±2 / Add +3 and the
            // client's odd-axis rule; anything past that is surfaced to order (RX).
            if (sph > 3 || cyl > 2 || add > 3 || q.isSpecialAxis()) {
                return price(prog ? (bb ? "RX_PROG_BB" : "RX_PROG") : (bb ? "RX_BF_BB" : "RX_BF"));
            }
            if (prog) return price(!bb ? "PROG" : sph <= 2 && add <= 2 ? "PROG_BB_2" : "PROG_BB_3");
            return price((bb ? "BF_BB_" : "BF_") + (sph <= 2 ? "2" : "3"));
        }

        long base;
        if ("PHOTOCHROMATIC".equals(q.getLensType())) {
            if (sph > 10) throw outOfRange();
            base = price((bb ? "PG_BB_" : "PG_") + (sph <= 4 ? "4" : sph <= 8 ? "8" : "10"));
        } else {
            if (sph > 4) throw outOfRange(); // clear SV only goes to ±4.00 — Colormatic covers higher
            base = price(bb ? "SV_CLEAR_BB" : "SV_CLEAR");
        }
        return cyl > 2 ? base + price("CYL_EXTRA") : base;
    }

    private long price(String code) {
        return options.findByCode(code).map(LensPricingOption::getPriceMinor).orElse(DEFAULTS.get(code));
    }

    private static double abs(BigDecimal v) {
        return v == null ? 0 : v.abs().doubleValue();
    }

    private static ApiException outOfRange() {
        return ApiException.badRequest("PRICE_ON_REQUEST",
                "This prescription is outside our standard price list — message us on WhatsApp and we'll quote it.");
    }
}
