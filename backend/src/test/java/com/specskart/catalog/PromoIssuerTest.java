package com.specskart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class PromoIssuerTest {

    @Autowired PromoIssuer issuer;
    @Autowired PromoCodeRepository promos;

    @Test
    void issuesOnePersonalExpiringCodePerLead() {
        UUID lead = UUID.randomUUID();

        PromoCode a = issuer.forFaceAnalysis(lead);
        assertThat(a.getCode()).startsWith("FIT-");
        assertThat(a.getLeadId()).isEqualTo(lead);
        assertThat(a.getDiscountType()).isEqualTo("PERCENT");
        assertThat(a.getDiscountValue()).isGreaterThan(0);
        assertThat(a.getExpiresAt()).isAfter(Instant.now());
        assertThat(a.getMaxRedemptions()).isEqualTo(1);
        assertThat(a.usable(0)).isTrue();

        // re-running analysis reuses the same live code
        PromoCode b = issuer.forFaceAnalysis(lead);
        assertThat(b.getId()).isEqualTo(a.getId());

        // and it validates as a normal cart promo
        assertThat(promos.findByCodeIgnoreCase(a.getCode())).isPresent();
    }
}
