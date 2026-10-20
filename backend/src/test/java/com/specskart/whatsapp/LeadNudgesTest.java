package com.specskart.whatsapp;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Chat -> shop visit: booking, the visit-day reminder, the one quote follow-up, staff reminders. */
@SpringBootTest
@ActiveProfiles("mock")
class LeadNudgesTest {

    @Autowired WhatsAppInboundService inbound;
    @Autowired WhatsAppProvider provider;
    @Autowired LeadRepository leads;
    @Autowired LeadNudges nudges;

    /** A far-future Tuesday 10:00, so "due by then" holds whenever the suite runs. */
    private static final ZonedDateTime TUESDAY_10AM = ZonedDateTime.of(2030, 1, 8, 10, 0, 0, 0, LeadNudges.LUSAKA);

    private static String someNumber() {
        return "26096" + (200000 + (int) (Math.random() * 700000));
    }

    private void say(String waId, String text, String button, String step) {
        inbound.process(new InboundMessage(waId, waId, "Mercy Tembo", text, button, "nudge:" + waId + ":" + step, Map.of()));
    }

    private int mark() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    private List<MockWhatsAppProvider.Sent> since(int mark, String waId) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return outbox.subList(mark, outbox.size()).stream().filter(s -> waId.equals(s.toWaId())).toList();
    }

    private Lead lead(String waId) {
        return leads.findByWhatsappWaId(waId).orElseThrow();
    }

    @Test
    void aCustomerBooksAFreeEyeTestFromTheChat() {
        String waId = someNumber();
        int mark = mark();
        say(waId, "Book my visit", LeadNudges.BTN_BOOK, "b1");

        var days = since(mark, waId).stream().flatMap(s -> s.buttons().stream()).toList();
        assertThat(days).isNotEmpty().allMatch(b -> b.id().startsWith(LeadNudges.ROW_BOOK_PREFIX));

        String pick = days.get(days.size() - 1).id();
        int booked = mark();
        say(waId, days.get(days.size() - 1).title(), pick, "b2");

        LocalDate day = LocalDate.parse(pick.substring(LeadNudges.ROW_BOOK_PREFIX.length()));
        assertThat(lead(waId).getVisitOn()).isEqualTo(day);
        assertThat(since(booked, waId)).anyMatch(s -> s.text() != null && s.text().contains("You're booked"));
    }

    @Test
    void theBookedDayGetsOneMorningReminder() {
        String waId = someNumber();
        say(waId, "hi", null, "r0");
        Lead l = lead(waId);
        l.setVisitOn(TUESDAY_10AM.toLocalDate());
        leads.save(l);

        int mark = mark();
        nudges.remindVisits(TUESDAY_10AM.withHour(8));
        nudges.remindVisits(TUESDAY_10AM.withHour(9));

        var mine = since(mark, waId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("See you today");
    }

    @Test
    void aQuoteThatWentQuietGetsOneFollowUpWithABookButton() {
        String waId = someNumber();
        say(waId, "How much for -2 lenses", null, "q1");
        assertThat(lead(waId).getQuoteFollowUpDueAt()).isNotNull();

        Lead l = lead(waId);
        l.setQuoteFollowUpDueAt(Instant.now().minusSeconds(60));
        leads.save(l);

        int mark = mark();
        nudges.followUpQuotes(TUESDAY_10AM);
        nudges.followUpQuotes(TUESDAY_10AM);

        var mine = since(mark, waId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("Just checking in");
        assertThat(mine.get(0).buttons()).anyMatch(b -> b.id().equals(LeadNudges.BTN_BOOK));
        assertThat(lead(waId).getQuoteFollowUpDueAt()).isNull();
    }

    @Test
    void aWaitingCustomerKeepsNudgingStaffUntilSomeoneReplies() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Audrina", "[image]", null, "nudge:" + waId + ":p",
                Map.of(), "media-" + waId, null));
        Lead l = lead(waId);
        assertThat(l.getAwaitingStaffSince()).isNotNull();
        l.setAwaitingStaffSince(Instant.now().minusSeconds(30 * 60));
        leads.save(l);

        nudges.nudgeStaff(TUESDAY_10AM);
        nudges.nudgeStaff(TUESDAY_10AM); // too soon for the second push
        assertThat(lead(waId).getStaffNudges()).isEqualTo(1);

        // every 5 minutes after that -- and it stops at the cap, never an endless loop
        for (int i = 0; i < 20; i++) {
            Lead w = lead(waId);
            if (w.getStaffNudgedAt() != null) w.setStaffNudgedAt(w.getStaffNudgedAt().minusSeconds(5 * 60));
            leads.save(w);
            nudges.nudgeStaff(TUESDAY_10AM);
        }
        assertThat(lead(waId).getStaffNudges()).isEqualTo(LeadNudges.MAX_NUDGES);

        nudges.staffReplied(l.getId());
        assertThat(lead(waId).getAwaitingStaffSince()).isNull();
    }

    @Test
    void nightTimeFollowUpsMoveToTheEveningBefore() {
        Instant threeAm = ZonedDateTime.of(2030, 1, 9, 3, 0, 0, 0, LeadNudges.LUSAKA).toInstant();
        assertThat(LeadNudges.daytime(threeAm).atZone(LeadNudges.LUSAKA).getHour()).isEqualTo(20);
        assertThat(LeadNudges.daytime(threeAm).atZone(LeadNudges.LUSAKA).getDayOfMonth()).isEqualTo(8);
        Instant noon = TUESDAY_10AM.withHour(12).toInstant();
        assertThat(LeadNudges.daytime(noon)).isEqualTo(noon);
    }

    @Test
    void askingForPicturesOfFramesIsAFramePhotosQuestion() {
        assertThat(WhatsAppBotService.classify("Do you have frames you can share on WhatsApp?", null)).isEqualTo(BotIntent.FRAME_PHOTOS);
        assertThat(WhatsAppBotService.classify("send me pics of your specs", null)).isEqualTo(BotIntent.FRAME_PHOTOS);
        assertThat(WhatsAppBotService.classify("Can I see some of the frames that you have available", null)).isEqualTo(BotIntent.FRAME_PHOTOS);
        assertThat(WhatsAppBotService.classify("I want photo chromatic lenses", null)).isEqualTo(BotIntent.EXPLORE_LENS);
        assertThat(WhatsAppBotService.classify("can I send a photo of my prescription", null)).isNotEqualTo(BotIntent.FRAME_PHOTOS);
    }

    @Test
    void aNamedDayIsRead() {
        LocalDate today = LocalDate.of(2026, 10, 20); // a Tuesday
        assertThat(WhatsAppBotService.requestedDay("i want to visit store on jan 1 2027", today)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(WhatsAppBotService.requestedDay("can i come on 1st january", today)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(WhatsAppBotService.requestedDay("are you open on 24 october", today)).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(WhatsAppBotService.requestedDay("can i come saturday", today)).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(WhatsAppBotService.requestedDay("coming tomorrow", today)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(WhatsAppBotService.requestedDay("lenses that darken in the sun", today)).isNull();
        assertThat(WhatsAppBotService.requestedDay("i was satisfied", today)).isNull();
    }

    @Test
    void askingToVisitOnAHolidaySaysClosedAndOffersTheNextOpenDays() {
        String waId = someNumber();
        int mark = mark();
        say(waId, "I want to visit store on Jan 1 2027", null, "h1");

        var mine = since(mark, waId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("closed on Friday 1 Jan 2027").contains("New Year's Day");
        assertThat(mine.get(0).buttons()).extracting(WhatsAppProvider.Button::id)
                .first().isEqualTo(LeadNudges.ROW_BOOK_PREFIX + "2027-01-02");
    }

    @Autowired AdminVisitController visits;

    @Test
    void aNoShowGetsOneSorryWeMissedYouUnlessStaffMarkedItDone() {
        String missed = someNumber(), came = someNumber();
        say(missed, "hi", null, "n0");
        say(came, "hi", null, "c0");
        for (String w : List.of(missed, came)) {
            Lead l = lead(w);
            l.setVisitOn(TUESDAY_10AM.toLocalDate().minusDays(1));
            leads.save(l);
        }
        visits.done(lead(came).getId());

        int mark = mark();
        nudges.followUpNoShows(TUESDAY_10AM);
        nudges.followUpNoShows(TUESDAY_10AM);

        var mine = since(mark, missed);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("Sorry we missed you");
        assertThat(mine.get(0).buttons()).isNotEmpty().allMatch(b -> b.id().startsWith(LeadNudges.ROW_BOOK_PREFIX));
        assertThat(since(mark, came)).isEmpty();
        // tapping a day in that message rebooks them, and the new booking's nudges start fresh
        var pick = mine.get(0).buttons().get(0);
        say(missed, pick.title(), pick.id(), "n1");
        Lead re = lead(missed);
        assertThat(re.getVisitOn()).isEqualTo(LocalDate.parse(pick.id().substring(LeadNudges.ROW_BOOK_PREFIX.length())));
        assertThat(re.getVisitMissedOn()).isNull();
        assertThat(re.getVisitNudgedOn()).isNull();
    }

    @Test
    void bookedForTodayAndNotInYetGetsOneStillComingAtTwo() {
        String waId = someNumber();
        say(waId, "hi", null, "s0");
        Lead l = lead(waId);
        l.setVisitOn(TUESDAY_10AM.toLocalDate());
        leads.save(l);

        int mark = mark();
        nudges.nudgeSameDay(TUESDAY_10AM.withHour(13));
        nudges.nudgeSameDay(TUESDAY_10AM.withHour(14));
        nudges.nudgeSameDay(TUESDAY_10AM.withHour(14));

        var mine = since(mark, waId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("Still coming in today").contains("17:00");
    }

    @Test
    void theBookingsPageGroupsByState() {
        LocalDate today = LocalDate.now(LeadNudges.LUSAKA);
        Lead l = new Lead();
        l.setVisitOn(today.minusDays(1));
        assertThat(AdminVisitController.view(l, today).state()).isEqualTo("MISSED");
        l.setVisitOn(today);
        assertThat(AdminVisitController.view(l, today).state()).isEqualTo("TODAY");
        l.setVisitedOn(today);
        assertThat(AdminVisitController.view(l, today).state()).isEqualTo("DONE");
    }
}
