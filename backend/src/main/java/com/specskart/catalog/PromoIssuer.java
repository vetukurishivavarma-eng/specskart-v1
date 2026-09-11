package com.specskart.catalog;

import com.specskart.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Issues personal, time-limited discount codes (one live code per lead). */
@Service
public class PromoIssuer {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String A = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final PromoCodeRepository promos;
    private final AppProperties props;

    public PromoIssuer(PromoCodeRepository promos, AppProperties props) {
        this.promos = promos;
        this.props = props;
    }

    /** The lead's live post-analysis code, creating one if they don't have an unexpired one. */
    @Transactional
    public PromoCode forFaceAnalysis(UUID leadId) {
        Instant now = Instant.now();
        return promos.findFirstByLeadIdAndActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(leadId, now)
                .orElseGet(() -> {
                    PromoCode p = new PromoCode();
                    p.setLeadId(leadId);
                    p.setCode(freshCode());
                    p.setDiscountType("PERCENT");
                    p.setDiscountValue(Math.max(1, props.promo().faceAnalysisPercent()));
                    p.setMaxRedemptions(1);
                    p.setExpiresAt(now.plus(Math.max(1, props.promo().faceAnalysisHours()), ChronoUnit.HOURS));
                    p.setActive(true);
                    return promos.save(p);
                });
    }

    /** A bigger, shorter-lived code for a lead who hasn't converted after the whole nurture
     *  sequence — one stronger final nudge instead of the standard face-analysis offer. */
    @Transactional
    public PromoCode forLastCall(UUID leadId) {
        Instant now = Instant.now();
        return promos.findFirstByLeadIdAndActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(leadId, now)
                .filter(p -> p.getDiscountValue() >= props.promo().lastCallPercent())
                .orElseGet(() -> {
                    PromoCode p = new PromoCode();
                    p.setLeadId(leadId);
                    p.setCode(freshCode());
                    p.setDiscountType("PERCENT");
                    p.setDiscountValue(Math.max(1, props.promo().lastCallPercent()));
                    p.setMaxRedemptions(1);
                    p.setExpiresAt(now.plus(Math.max(1, props.promo().lastCallHours()), ChronoUnit.HOURS));
                    p.setActive(true);
                    return promos.save(p);
                });
    }

    private String freshCode() {
        for (int i = 0; i < 12; i++) {
            StringBuilder sb = new StringBuilder("FIT-");
            for (int j = 0; j < 5; j++) sb.append(A.charAt(RNG.nextInt(A.length())));
            String code = sb.toString();
            if (promos.findByCodeIgnoreCase(code).isEmpty()) return code;
        }
        return "FIT-" + System.currentTimeMillis();
    }
}
