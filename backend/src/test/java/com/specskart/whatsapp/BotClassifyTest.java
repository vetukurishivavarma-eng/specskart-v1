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

    @Test
    void aTypedPowerIsReadOnlyWhenItIsUnambiguous() {
        assertThat(WhatsAppBotService.typedPower("how much for -2 lenses")).isEqualTo("-2");
        assertThat(WhatsAppBotService.typedPower("how much is colourmatic 0.5")).isEqualTo("0.5");
        assertThat(WhatsAppBotService.typedPower("right -2.00 left -2.75 price")).isEqualTo("-2.75");
        assertThat(WhatsAppBotService.typedPower("2 pairs of lenses k450")).isNull();
        assertThat(WhatsAppBotService.typedPower("-2.00 cyl -1.25 how much")).isNull();
        assertThat(WhatsAppBotService.typedPower("bifocal -1 +2 price")).isNull();
    }

    @Test
    void openingHoursAreAnsweredForRightNowInLusaka() {
        var lusaka = java.time.ZoneId.of("Africa/Lusaka");
        var tuesdayNoon = java.time.ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, lusaka);
        var fridayEvening = java.time.ZonedDateTime.of(2026, 10, 23, 18, 0, 0, 0, lusaka);
        var saturdayAfternoon = java.time.ZonedDateTime.of(2026, 10, 24, 15, 0, 0, 0, lusaka);
        var mondayEarly = java.time.ZonedDateTime.of(2026, 10, 26, 7, 0, 0, 0, lusaka);
        assertThat(WhatsAppBotService.openNowLine(tuesdayNoon)).contains("open now, until 17:00");
        assertThat(WhatsAppBotService.openNowLine(fridayEvening.plusWeeks(1))).contains("closed").contains("tomorrow");
        assertThat(WhatsAppBotService.openNowLine(saturdayAfternoon)).contains("on Monday");
        assertThat(WhatsAppBotService.openNowLine(mondayEarly)).contains("today at 8:00");
        assertThat(WhatsAppBotService.isOpen(mondayEarly.withHour(8).withMinute(30))).isTrue();
        // holidays: Fri 23 Oct evening -> Sat 24 is Independence Day -> Monday; Christmas (Fri) -> Saturday
        var independenceDay = java.time.ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, lusaka);
        var christmas = java.time.ZonedDateTime.of(2026, 12, 25, 10, 0, 0, 0, lusaka);
        assertThat(WhatsAppBotService.openNowLine(fridayEvening)).contains("on Monday");
        assertThat(WhatsAppBotService.isOpen(independenceDay)).isFalse();
        assertThat(WhatsAppBotService.openNowLine(independenceDay)).contains("closed today for Independence Day").contains("on Monday");
        assertThat(WhatsAppBotService.openNowLine(christmas)).contains("Christmas").contains("tomorrow");
        // Easter 2027: Thu 25 Mar evening -> Fri/Sat holidays, Sun closed, Mon Easter Monday -> Tuesday
        assertThat(WhatsAppBotService.nextOpening(java.time.ZonedDateTime.of(2027, 3, 25, 18, 0, 0, 0, lusaka)))
                .isEqualTo("on Tuesday");
        assertThat(WhatsAppBotService.kwacha(45_000)).isEqualTo("K450");
        assertThat(WhatsAppBotService.kwacha(99_900)).isEqualTo("K999");
    }

    /** A broadcast's quick-reply button ("Spin") comes back with its own text as the id. */
    @Test
    void aTemplateQuickReplyIsReadByItsText() {
        assertThat(WhatsAppBotService.classify("Spin", "Spin")).isEqualTo(BotIntent.SPIN);
        assertThat(WhatsAppBotService.classify("Book my visit", WhatsAppBotService.BTN_SPIN)).isEqualTo(BotIntent.SPIN); // our ids still win
    }

    @Test
    void accessoryQuestionsGetTheAccessoriesAnswer() {
        assertThat(WhatsAppBotService.classify("do you sell lens cleaning solution?", null)).isEqualTo(BotIntent.ACCESSORIES);
        assertThat(WhatsAppBotService.classify("how much is a pouch", null)).isEqualTo(BotIntent.ACCESSORIES);
        assertThat(WhatsAppBotService.classify("I need a glasses case", null)).isEqualTo(BotIntent.ACCESSORIES);
        assertThat(WhatsAppBotService.classify("do you have readers?", null)).isEqualTo(BotIntent.ACCESSORIES);
        assertThat(WhatsAppBotService.classify("in case my glasses break", null)).isEqualTo(BotIntent.FRAMES);
        assertThat(WhatsAppBotService.classify("how much are photochromic lenses", null)).isEqualTo(BotIntent.PRICE);
    }
}
