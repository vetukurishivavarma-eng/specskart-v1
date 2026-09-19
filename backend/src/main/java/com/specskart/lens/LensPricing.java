package com.specskart.lens;

import com.specskart.membership.MembershipService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Lens pricing — staff-editable via {@code /api/admin/lens-pricing} (and the Specskart POS
 * app), backed by {@link LensPricingOption}. Falls back to the original placeholder values if
 * a row is somehow missing, so a bad edit never breaks quoting.
 */
@Service
class LensPricing {

    private static final long CLEAR_MINOR = 25_000;
    private static final long PHOTOCHROMATIC_MINOR = 45_000;
    private static final long BLUE_BLOCK_MINOR = 8_000;
    private static final long BIFOCAL_MINOR = 15_000;
    private static final long PROGRESSIVE_MINOR = 35_000;

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
        long total = priceOf("PHOTOCHROMATIC".equals(q.getLensType()) ? "PHOTOCHROMATIC" : "CLEAR",
                "PHOTOCHROMATIC".equals(q.getLensType()) ? PHOTOCHROMATIC_MINOR : CLEAR_MINOR);
        if (q.isBlueBlock()) total += priceOf("BLUE_BLOCK", BLUE_BLOCK_MINOR);
        if (q.getAddPower() != null && q.getAddPower().compareTo(BigDecimal.ZERO) > 0) {
            boolean progressive = "PROGRESSIVE".equals(q.getLensStructure());
            total += progressive ? priceOf("PROGRESSIVE", PROGRESSIVE_MINOR) : priceOf("BIFOCAL", BIFOCAL_MINOR);
        }
        return total;
    }

    /** The undiscounted price, for showing a member what they saved. */
    long listQuote(LensInquiry q) {
        return quoteBeforeDiscount(q);
    }

    private long priceOf(String code, long fallback) {
        return options.findByCode(code).map(LensPricingOption::getPriceMinor).orElse(fallback);
    }
}
