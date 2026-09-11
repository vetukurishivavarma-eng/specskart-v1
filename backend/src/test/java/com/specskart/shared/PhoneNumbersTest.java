package com.specskart.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void zambianLocalFormatGetsTheCountryCode() {
        assertThat(PhoneNumbers.normalize("0977123456", "260")).isEqualTo("260977123456");
        assertThat(PhoneNumbers.normalize("0972 809 599", "260")).isEqualTo("260972809599");
    }

    @Test
    void alreadyInternationalIsLeftAsIs() {
        assertThat(PhoneNumbers.normalize("+260977123456", "260")).isEqualTo("260977123456");
        assertThat(PhoneNumbers.normalize("260977123456", "260")).isEqualTo("260977123456");
        assertThat(PhoneNumbers.normalize("00260977123456", "260")).isEqualTo("260977123456");
    }

    @Test
    void bareLocalNumberWithoutTheTrunkZeroStillGetsTheCountryCode() {
        assertThat(PhoneNumbers.normalize("977123456", "260")).isEqualTo("260977123456");
    }

    @Test
    void blankOrNullIsNull() {
        assertThat(PhoneNumbers.normalize(null, "260")).isNull();
        assertThat(PhoneNumbers.normalize("   ", "260")).isNull();
    }
}
