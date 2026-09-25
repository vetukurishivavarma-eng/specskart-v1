package com.specskart.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaTemplateParamTest {

    /** The "ready to collect" line that Meta refused with #132018 in prod. */
    @Test
    void flattensLineBreaksTabsAndLongSpaceRuns() {
        String ready = "your lenses are ready! Collect them at:\nSpecskart, Cairo Rd\n📍 https://maps/x"
                + "\n\nCome in yourself\t—     quote LENS-1.";
        String p = MetaWhatsAppProvider.param(ready);
        assertThat(p).doesNotContain("\n").doesNotContain("\t").doesNotContain("    ");
        assertThat(p).isEqualTo("your lenses are ready! Collect them at: Specskart, Cairo Rd 📍 https://maps/x"
                + " Come in yourself — quote LENS-1.");
        assertThat(MetaWhatsAppProvider.param(null)).isEmpty();
    }
}
