package com.specskart.pos;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** A pasted download link with spaces in it must still download (2026-10-21 locked every till). */
class AppReleaseLinkTest {

    static final String GOOD = "https://github.com/vetukurishivavarma-eng/specskart-pos/releases/download/v1.29.0/specskart-pos-1.29.0.apk";

    @Test
    void spacesAreStrippedOnSaveAndOnRead() {
        AppRelease r = new AppRelease();
        r.setDownloadUrl(" https://github.com/vetukurishivavarma-eng /specskart-pos/releases/download/v1.29.0 /specskart-pos-1.29.0.apk ");
        assertThat(r.getDownloadUrl()).isEqualTo(GOOD);

        // a row saved before the fix
        ReflectionTestUtils.setField(r, "downloadUrl", "https://github.com/vetukurishivavarma-eng /specskart-pos/releases/download/v1.29.0 /specskart-pos-1.29.0.apk");
        assertThat(r.getDownloadUrl()).isEqualTo(GOOD);
    }
}
