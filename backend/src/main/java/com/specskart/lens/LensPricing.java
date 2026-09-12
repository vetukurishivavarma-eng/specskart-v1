package com.specskart.lens;

import java.math.BigDecimal;

/**
 * ponytail: hardcoded placeholder pricing — the client said they'll give the real formula
 * later. Swap the body of quote() when it arrives; every caller already goes through here.
 */
final class LensPricing {

    private LensPricing() {}

    private static final long CLEAR_MINOR = 25_000;          // K250
    private static final long PHOTOCHROMATIC_MINOR = 45_000; // K450
    private static final long BLUE_BLOCK_MINOR = 8_000;       // K80
    private static final long BIFOCAL_MINOR = 15_000;         // K150
    private static final long PROGRESSIVE_MINOR = 35_000;     // K350

    static long quote(LensInquiry q) {
        long total = "PHOTOCHROMATIC".equals(q.getLensType()) ? PHOTOCHROMATIC_MINOR : CLEAR_MINOR;
        if (q.isBlueBlock()) total += BLUE_BLOCK_MINOR;
        if (q.getAddPower() != null && q.getAddPower().compareTo(BigDecimal.ZERO) > 0) {
            total += "PROGRESSIVE".equals(q.getLensStructure()) ? PROGRESSIVE_MINOR : BIFOCAL_MINOR;
        }
        return total;
    }
}
