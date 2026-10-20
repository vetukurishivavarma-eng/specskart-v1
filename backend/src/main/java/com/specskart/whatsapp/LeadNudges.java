package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import com.specskart.lead.FollowUpState;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import com.specskart.lens.LensInquiryService;
import com.specskart.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Getting a chat into the shop (2026-10-20 lead audit: 46 leads, 0 orders, a third waited 6-22h
 * for a person). Three timed things, all free-form inside Meta's 24h window -- no template:
 * <ul>
 *   <li>a booked eye-test day gets a reminder that morning;</li>
 *   <li>a price that went quiet gets ONE "would you like to come in?" ~20h after their last message;</li>
 *   <li>a customer promised a person is re-pushed to the staff phones while nobody answers.</li>
 * </ul>
 */
@Component
public class LeadNudges {

    private static final Logger log = LoggerFactory.getLogger(LeadNudges.class);

    static final String BTN_BOOK = "BOOK_VISIT";
    /** A tapped day: {@code BOOK:2026-10-21}. */
    static final String ROW_BOOK_PREFIX = "BOOK:";
    /** The POS app's Bookings page -- where every eye-test push lands. */
    static final String BOOKINGS_PATH = "/bookings";
    static final ZoneId LUSAKA = ZoneId.of("Africa/Lusaka");
    static final Duration QUIET_AFTER = Duration.ofHours(20);
    static final Duration FOLLOW_UP_GAP = Duration.ofDays(7);
    /** Staff reminders: every 5 min while nobody answers, then give up -- 6 pushes = half an hour. */
    static final Duration FIRST_NUDGE = Duration.ofMinutes(5);
    static final Duration NEXT_NUDGE = Duration.ofMinutes(5);
    static final int MAX_NUDGES = 6;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH);

    private final LeadRepository leads;
    private final WhatsAppMessageRepository messages;
    private final WhatsAppProvider provider;
    private final ServiceWindow window;
    private final PushService push;
    private final LensInquiryService lensInquiries;
    private final AppProperties props;

    public LeadNudges(LeadRepository leads, WhatsAppMessageRepository messages, WhatsAppProvider provider,
                      ServiceWindow window, PushService push, LensInquiryService lensInquiries, AppProperties props) {
        this.leads = leads;
        this.messages = messages;
        this.provider = provider;
        this.window = window;
        this.push = push;
        this.lensInquiries = lensInquiries;
        this.props = props;
    }

    // ---- hooks -----------------------------------------------------------------------------

    /** A price or a staff answer went out: schedule the one follow-up, unless one went this week. */
    public void quoted(UUID leadId) {
        update(leadId, lead -> {
            if (lead.getQuoteFollowedUpAt() != null && lead.getQuoteFollowedUpAt().isAfter(Instant.now().minus(FOLLOW_UP_GAP))) return;
            if (hasUpcomingVisit(lead)) return;
            messages.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(lead.getId(), "INBOUND")
                    .map(m -> daytime(m.getCreatedAt().plus(QUIET_AFTER)))
                    .filter(due -> due.isAfter(Instant.now()))
                    .ifPresent(lead::setQuoteFollowUpDueAt);
        });
    }

    /** They wrote again, so the conversation is alive: a pending follow-up moves to 20h after this. */
    public void customerWrote(UUID leadId) {
        update(leadId, lead -> {
            if (lead.getQuoteFollowUpDueAt() == null) return;
            Instant due = daytime(Instant.now().plus(QUIET_AFTER));
            lead.setQuoteFollowUpDueAt(due.isAfter(Instant.now()) ? due : null);
        });
    }

    /** The bot promised a person (or they wrote into a staff chat): start the staff reminders. */
    public void awaitingStaff(UUID leadId) {
        update(leadId, lead -> {
            if (lead.getAwaitingStaffSince() != null) return;
            lead.setAwaitingStaffSince(Instant.now());
            lead.setStaffNudges(0);
            lead.setStaffNudgedAt(null);
        });
    }

    /** A staff member answered from the POS chat. */
    public void staffReplied(UUID leadId) {
        update(leadId, lead -> {
            lead.setAwaitingStaffSince(null);
            lead.setStaffNudges(0);
            lead.setStaffNudgedAt(null);
        });
        quoted(leadId);
    }

    /** Book the free eye test for that day; staff phones are told. A same-day booking needs no reminder. */
    public void book(UUID leadId, LocalDate day) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null) return;
        lead.setVisitOn(day);
        lead.setVisitRemindedOn(day.equals(LocalDate.now(LUSAKA)) ? day : null);
        lead.setQuoteFollowUpDueAt(null);
        leads.save(lead);
        push.notifyStaff("👁️ Eye test booked: " + who(lead), DAY.format(day), BOOKINGS_PATH);
    }

    // ---- the job -----------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT2M")
    public void run() {
        ZonedDateTime now = ZonedDateTime.now(LUSAKA);
        if (props.whatsapp().nurtureOn()) {
            remindVisits(now);
            followUpQuotes(now);
            nudgeSameDay(now);
            followUpNoShows(now);
        }
        nudgeStaff(now);
        staffDigest(now);
    }

    // ponytail: in memory -- a restart between 8:00 and 9:00 sends the day's list twice; a column if that bites
    private LocalDate digestSentOn;

    /** 8:00-9:00: one push listing today's eye tests; tapping it opens the Bookings page. */
    void staffDigest(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        if (now.getHour() != 8 || today.equals(digestSentOn)) return;
        digestSentOn = today;
        List<Lead> booked = leads.findByVisitOnBetweenOrderByVisitOnAscCreatedAtAsc(today, today);
        if (booked.isEmpty()) return;
        String names = String.join(", ", booked.stream().map(LeadNudges::who).toList());
        push.notifyStaff("👁️ " + booked.size() + " eye test" + (booked.size() == 1 ? "" : "s") + " booked today",
                names.length() > 180 ? names.substring(0, 180) + "…" : names, BOOKINGS_PATH);
    }

    /** Booked for today and not in yet: one "still coming?" 3 hours before closing (14:00, Sat 11:00). */
    void nudgeSameDay(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        int close = WhatsAppBotService.closingHour(today);
        if (close == 0 || now.getHour() != close - 3) return;
        for (Lead lead : leads.findTop100ByVisitOnAndVisitedOnIsNullAndVisitNudgedOnIsNull(today)) {
            each(lead, l -> {
                l.setVisitNudgedOn(today);
                leads.save(l);
                if (!window.isOpen(waId(l)) || bookedWithin(l, Duration.ofHours(2))) return;
                String body = "Still coming in today" + firstName(l) + "? 👋 We're open until " + close + ":00 "
                        + "for your free eye check-up." + shops();
                provider.sendButtons(waId(l), body, List.of(new WhatsAppProvider.Button(BTN_BOOK, "Another day")));
                log(l, "interactive", body);
            });
        }
    }

    /** The morning after a missed booking (up to 3 days back): one "sorry we missed you" with new days. */
    void followUpNoShows(ZonedDateTime now) {
        if (now.getHour() < 8 || now.getHour() >= 12) return;
        LocalDate today = now.toLocalDate();
        for (Lead lead : leads.findTop100ByVisitOnBetweenAndVisitedOnIsNullAndVisitMissedOnIsNull(today.minusDays(3), today.minusDays(1))) {
            each(lead, l -> {
                l.setVisitMissedOn(today);
                leads.save(l);
                if (!window.isOpen(waId(l))) return; // a rebooking moves visit_on out of this range
                String body = "Sorry we missed you" + firstName(l) + "! 👋 Would you like to pick another day "
                        + "for your free eye check-up? 👁️";
                provider.sendButtons(waId(l), body, WhatsAppBotService.bookingDayButtons(today, now));
                log(l, "interactive", body);
            });
        }
    }

    /** Staff tapped "Check-up done" on the Bookings page; null undoes a mis-tap. */
    public void markVisited(UUID leadId, LocalDate day) {
        update(leadId, lead -> lead.setVisitedOn(day));
    }

    private boolean bookedWithin(Lead lead, Duration d) {
        return messages.findByLeadIdAndDirectionAndCreatedAtAfter(lead.getId(), "OUTBOUND", Instant.now().minus(d)).stream()
                .anyMatch(m -> m.getBody() != null && m.getBody().startsWith("✅ You're booked"));
    }

    /** 7:00-12:00 on the booked day, once. */
    void remindVisits(ZonedDateTime now) {
        if (now.getHour() < 7 || now.getHour() >= 12) return;
        LocalDate today = now.toLocalDate();
        for (Lead lead : leads.findTop100ByVisitOnAndVisitRemindedOnIsNull(today)) {
            each(lead, l -> {
                l.setVisitRemindedOn(today);
                leads.save(l);
                if (!window.isOpen(waId(l))) return;
                send(l, "Good morning" + firstName(l) + " 👋 See you today for your free eye check-up!\n"
                        + "We're open " + WhatsAppBotService.OPENS_AT + ":00–"
                        + WhatsAppBotService.closingHour(today) + ":00. Bring your old glasses or "
                        + "prescription if you have them." + shops());
            });
        }
    }

    /** The one follow-up after a price, daytime only, only while their 24h window is open. */
    void followUpQuotes(ZonedDateTime now) {
        if (now.getHour() < 7 || now.getHour() >= 21) return;
        for (Lead lead : leads.findTop100ByQuoteFollowUpDueAtLessThanEqual(now.toInstant())) {
            each(lead, l -> {
                l.setQuoteFollowUpDueAt(null);
                leads.save(l);
                if (l.getFollowUpState() == FollowUpState.OPTED_OUT || l.getStatus() == LeadStatus.CONVERTED
                        || hasUpcomingVisit(l) || !window.isOpen(waId(l))) return;
                String body = "Hi" + firstName(l) + " 👋 Just checking in: would you like to come in for your "
                        + "glasses? The eye check-up is free and you can try frames on in the shop." + shops()
                        + "\n\nTap below and we'll expect you.";
                provider.sendButtons(waId(l), body, List.of(new WhatsAppProvider.Button(BTN_BOOK, "Book my visit")));
                log(l, "interactive", body);
                l.setQuoteFollowedUpAt(Instant.now());
                leads.save(l);
            });
        }
    }

    /** While the shop is open: 5 min after the promise, then every 5 min, at most {@link #MAX_NUDGES} pushes. */
    void nudgeStaff(ZonedDateTime now) {
        if (!WhatsAppBotService.isOpen(now)) return;
        Instant at = now.toInstant();
        for (Lead lead : leads.findTop100ByAwaitingStaffSinceLessThanEqualAndStaffNudgesLessThan(at.minus(FIRST_NUDGE), MAX_NUDGES)) {
            each(lead, l -> {
                // 30s slack: the job runs every minute, so "5 min since the last push" lands on the 5th run, not the 6th
                if (l.getStaffNudgedAt() != null && l.getStaffNudgedAt().isAfter(at.minus(NEXT_NUDGE).plusSeconds(30))) return;
                var last = messages.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(l.getId(), "INBOUND");
                // their 24h window shut: staff can't free-text them any more, so stop buzzing
                if (last.isEmpty() || last.get().getCreatedAt().isBefore(Instant.now().minus(Duration.ofHours(24)))) {
                    l.setAwaitingStaffSince(null);
                    leads.save(l);
                    return;
                }
                String said = last.get().getBody() == null ? "(a photo or file)" : last.get().getBody();
                long mins = Duration.between(l.getAwaitingStaffSince(), at).toMinutes();
                // a message from last night: "since 22:14" reads better than "waiting 600 min"
                String waited = mins < 120 ? mins + " min"
                        : "since " + DateTimeFormatter.ofPattern("HH:mm").format(l.getAwaitingStaffSince().atZone(LUSAKA));
                boolean lastOne = l.getStaffNudges() + 1 == MAX_NUDGES;
                push.notifyStaff("⏰ " + who(l) + " waiting " + waited + " for a reply" + (lastOne ? " (last reminder)" : ""),
                        (said.length() > 150 ? said.substring(0, 150) + "…" : said),
                        "/chat/" + waId(l));
                l.setStaffNudges(l.getStaffNudges() + 1);
                l.setStaffNudgedAt(at);
                leads.save(l);
            });
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Keep automated messages to 7:00-21:00 Lusaka: a night time moves back to 20:00 the evening before. */
    static Instant daytime(Instant t) {
        ZonedDateTime z = t.atZone(LUSAKA);
        if (z.getHour() >= 21) z = z.withHour(20).withMinute(0);
        else if (z.getHour() < 7) z = z.minusDays(1).withHour(20).withMinute(0);
        return z.toInstant();
    }

    private boolean hasUpcomingVisit(Lead lead) {
        return lead.getVisitOn() != null && !lead.getVisitOn().isBefore(LocalDate.now(LUSAKA));
    }

    private void update(UUID leadId, Consumer<Lead> change) {
        leads.findById(leadId).ifPresent(lead -> { change.accept(lead); leads.save(lead); });
    }

    private void each(Lead lead, Consumer<Lead> work) {
        try { work.accept(lead); }
        catch (Exception e) { log.warn("lead nudge failed for lead {}: {}", lead.getId(), e.getMessage()); }
    }

    private void send(Lead lead, String text) {
        provider.sendText(waId(lead), text);
        log(lead, "text", text);
    }

    private void log(Lead lead, String type, String body) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(lead.getId());
        m.setDirection("OUTBOUND");
        m.setMessageType(type);
        m.setBody(body);
        m.setStatus("sent");
        messages.save(m);
    }

    private String shops() {
        String s = lensInquiries.shopLines();
        return s.isBlank() ? "" : "\n\n" + s;
    }

    private static String firstName(Lead lead) {
        return lead.getName() != null && !lead.getName().isBlank() ? " " + lead.getName().split(" ")[0] : "";
    }

    private static String who(Lead lead) {
        return lead.getName() != null && !lead.getName().isBlank() ? lead.getName() : "+" + waId(lead);
    }

    private static String waId(Lead lead) {
        return lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
    }
}
