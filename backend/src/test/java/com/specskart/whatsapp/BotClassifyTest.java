package com.specskart.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotClassifyTest {

    @Test
    void facebookIsNotAFaceQuestion() {
        // The Page button's prefilled text used to hit "face" and get the frame-finder link.
        assertThat(WhatsAppBotService.classify("Hi Specskart, I found you on Facebook", null)).isEqualTo(BotIntent.UNKNOWN);
        assertThat(WhatsAppBotService.classify("what is my face shape", null)).isEqualTo(BotIntent.FIND_FRAMES);
    }

    @Test
    void adPrefillAsksForMoreInfo() {
        assertThat(WhatsAppBotService.classify("Hi, I found you on Facebook. Can I have more information?", null)).isEqualTo(BotIntent.MORE_INFO);
        assertThat(WhatsAppBotService.classify("hi, can I have more information", null)).isEqualTo(BotIntent.MORE_INFO);
    }
}
