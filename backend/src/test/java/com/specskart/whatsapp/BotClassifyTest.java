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
        // a lens price question is answered with the price (2026-10-20), not the configurator link
        assertThat(WhatsAppBotService.classify("lens price please", null)).isEqualTo(BotIntent.PRICE);
        assertThat(WhatsAppBotService.classify("-6.00", null)).isEqualTo(BotIntent.PRICE);
        assertThat(WhatsAppBotService.classify("I want photo chromatic lenses", null)).isEqualTo(BotIntent.EXPLORE_LENS);
        // "where" alone is not "where is my order"
        assertThat(WhatsAppBotService.classify("where are you located", null)).isNotEqualTo(BotIntent.TRACK_ORDER);
        assertThat(WhatsAppBotService.classify("where is my order", null)).isEqualTo(BotIntent.TRACK_ORDER);
        assertThat(WhatsAppBotService.classify(null, "SUITS_YOU")).isEqualTo(BotIntent.SUITS_YOU);
    }
}
