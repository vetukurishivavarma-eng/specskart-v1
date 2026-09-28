package com.specskart.payment;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaidInFullTest {

    @Test
    void acceptsOnlyTheFullAmountInTheOrdersCurrency() {
        assertThat(FlutterwaveProvider.paidInFull(Map.of("amount", 780, "currency", "ZMW"), 78000, "ZMW")).isTrue();
        assertThat(FlutterwaveProvider.paidInFull(Map.of("amount", "780.00", "currency", "zmw"), 78000, "ZMW")).isTrue();
        assertThat(FlutterwaveProvider.paidInFull(Map.of("amount", 1, "currency", "ZMW"), 78000, "ZMW")).isFalse();
        assertThat(FlutterwaveProvider.paidInFull(Map.of("amount", 780, "currency", "NGN"), 78000, "ZMW")).isFalse();
        assertThat(FlutterwaveProvider.paidInFull(Map.of("currency", "ZMW"), 78000, "ZMW")).isFalse();
    }
}
