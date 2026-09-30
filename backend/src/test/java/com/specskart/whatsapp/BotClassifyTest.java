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

    @Test
    void priceAndFramesQuestionsGetTheClientsFramesFlow() {
        assertThat(WhatsAppBotService.classify("what is the price of frames?", null)).isEqualTo(BotIntent.PRICE);
        assertThat(WhatsAppBotService.classify("how much do your specs cost", null)).isEqualTo(BotIntent.PRICE);
        assertThat(WhatsAppBotService.classify("do you have spectacles for kids", null)).isEqualTo(BotIntent.FRAMES);
        assertThat(WhatsAppBotService.classify("I want new glasses", null)).isEqualTo(BotIntent.FRAMES);
        // a lens question is still the lens funnel, even with a price in it
        assertThat(WhatsAppBotService.classify("lens price please", null)).isEqualTo(BotIntent.EXPLORE_LENS);
        assertThat(WhatsAppBotService.classify(null, "SUITS_YOU")).isEqualTo(BotIntent.SUITS_YOU);
    }
}
