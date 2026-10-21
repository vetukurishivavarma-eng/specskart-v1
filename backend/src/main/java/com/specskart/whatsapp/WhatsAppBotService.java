package com.specskart.whatsapp;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.CatalogService;
import com.specskart.catalog.Product;
import com.specskart.config.AppProperties;
import com.specskart.framefinder.FrameFinderService;
import com.specskart.lens.LensInquiryService;
import com.specskart.membership.Membership;
import com.specskart.membership.MembershipService;
import com.specskart.order.CartService;
import com.specskart.order.OrderNotificationService;
import com.specskart.order.OrderQueryService;
import com.specskart.order.ReviewCaptureService;
import com.specskart.shared.TrackOption;
import com.specskart.shared.TrackUpdate;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadFollowUpService;
import com.specskart.lead.LeadService;
import com.specskart.lead.LeadStatus;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Deterministic first-line chatbot. No LLM. Maps button ids / keywords to a fixed set of intents
 * and replies with WhatsApp interactive messages.
 */
@Service
public class WhatsAppBotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBotService.class);

    static final String BTN_FIND = "FIND_FRAMES";
    static final String BTN_EXPLORE = "EXPLORE_FRAMES";
    static final String BTN_WEBSITE = "VISIT_WEBSITE";
    static final String BTN_RESULTS_FRAMES = "RESULTS_SHOW_FRAMES";
    static final String BTN_RESULTS_NOT_NOW = "RESULTS_NOT_NOW";
    static final String BTN_HELP_CHOOSE = "HELP_CHOOSE";
    static final String BTN_EXPLORE_LENS = "EXPLORE_LENS";
    static final String BTN_TRACK_ORDER = "TRACK_ORDER";
    static final String BTN_MENU = "MENU";
    static final String BTN_CARE = "CARE";
    static final String BTN_FAQ = "FAQ";
    static final String BTN_SUITS = "SUITS_YOU";
    static final String BTN_FRAME_PHOTOS = "FRAME_PHOTOS";
    static final String BTN_SPIN = "SPIN";
    /** How many frame photos one "show me frames" sends -- enough to browse, not a flood. */
    static final int FRAME_PHOTOS = 5;
    /** A tapped FAQ row: {@code FAQ:<uuid>}, or {@code FAQ_MORE:<offset>} for the next page. */
    static final String ROW_FAQ_PREFIX = "FAQ:";
    static final String ROW_FAQ_MORE_PREFIX = "FAQ_MORE:";
    /** A list row that buys one specific product: {@code BUY:<slug>}. */
    static final String ROW_BUY_PREFIX = "BUY:";
    /** A list row naming one of the customer's own orders: {@code TRACK:L:<uuid>} or {@code TRACK:O:<orderNo>}. */
    static final String ROW_TRACK_PREFIX = "TRACK:";
    static final String BTN_BUDGET_LOW = "BUDGET_LOW";
    static final String BTN_BUDGET_MED = "BUDGET_MED";
    static final String BTN_BUDGET_HIGH = "BUDGET_HIGH";

    /** "-6.00", "+1.25", "-16": a prescription typed into the chat. */
    static final java.util.regex.Pattern RX_NUMBER = java.util.regex.Pattern.compile("(^|\\s)[+-]\\s?\\d+(\\.\\d+)?\\b");
    static final java.util.regex.Pattern LENS_WORDS =
            java.util.regex.Pattern.compile("lens|colou?r ?matic|photo ?-?chrom|blue ?-?(block|light)|transition");
    /** How long the same answer, a menu, or the "someone will reply" note isn't sent again. */
    static final java.time.Duration REPEAT_WINDOW = java.time.Duration.ofHours(2);
    /** Every variant of the "a person will reply" note contains this -- it's how a repeat is spotted. */
    static final String HANDOFF_MARK = "from our team will reply";
    static final java.time.Duration HANDOFF_WINDOW = java.time.Duration.ofMinutes(30);
    static final java.time.Duration PHOTO_ACK_WINDOW = java.time.Duration.ofMinutes(5);
    static final java.util.regex.Pattern POWER =
            java.util.regex.Pattern.compile("(?<![\\w.])([+-]\\s?)?(\\d{1,2}(?:\\.\\d{1,2})?)(?![\\w%.])");
    static final java.util.regex.Pattern MULTIFOCAL_WORDS =
            java.util.regex.Pattern.compile("\\bcyl|\\badd\\b|axis|bifocal|progressive|varifocal|reading|astigm");

    /**
     * Typed questions the FAQs already answer, matched to the FAQ by its title. First match wins,
     * so the order matters ("do you offer insurance cover" is insurance, not the offer). Admins
     * edit the answers in the POS app; renaming a title there just unhooks it from these words.
     */
    private static final List<Map.Entry<java.util.regex.Pattern, String>> FAQ_KEYWORDS = List.of(
            faqKey("contact ?lens", "Contact lenses"),
            faqKey("nhima", "NHIMA"),
            faqKey("insur|prudential|one ?life|zisc|medical aid|\\bcover", "Insurance"),
            faqKey("eye ?(test|exam|check)|check ?-?up|examination", "Eye check-up"),
            faqKey("promo|discount|50 ?%|\\bsale\\b|\\boffers?\\b|slash", "Offers"),
            faqKey("locat|where (are|r) (you|u)|were are you|where is (the|your) (shop|store|clinic|office)"
                    + "|where the|address|direction|visit (the|your) (shop|store)|\\bmap\\b", "Where are you?"),
            faqKey("\\bopen|opening|hours|saturday|sunday|weekend|closing", "Opening hours"),
            faqKey("deliver", "Delivery"));

    private static Map.Entry<java.util.regex.Pattern, String> faqKey(String regex, String title) {
        return Map.entry(java.util.regex.Pattern.compile(regex), title);
    }

    private static final java.util.regex.Pattern BUY_PATTERN =
            java.util.regex.Pattern.compile("(?i)^\\s*buy\\s*([1-3])\\s*$");

    private final WhatsAppProvider provider;
    private final WhatsAppMessageRepository messages;
    private final FrameFinderService frameFinder;
    private final LeadService leadService;
    private final AnalyticsService analytics;
    private final AppProperties props;
    private final CatalogService catalog;
    private final CartService carts;
    private final LeadFollowUpService followUp;
    private final ReviewCaptureService reviewCapture;
    private final OrderQueryService orderQuery;
    private final LensInquiryService lensInquiries;
    private final MembershipService memberships;
    private final FaqRepository faqs;
    private final com.specskart.push.PushService push;
    private final com.specskart.spin.SpinService spins;
    private final LeadNudges nudges;
    private final com.specskart.catalog.ProductImageRepository productImages;

    public WhatsAppBotService(WhatsAppProvider provider, WhatsAppMessageRepository messages,
                              FrameFinderService frameFinder, LeadService leadService,
                              AnalyticsService analytics, AppProperties props,
                              CatalogService catalog, CartService carts, LeadFollowUpService followUp,
                              ReviewCaptureService reviewCapture, OrderQueryService orderQuery,
                              LensInquiryService lensInquiries, MembershipService memberships,
                              FaqRepository faqs, com.specskart.push.PushService push,
                              LeadNudges nudges, com.specskart.catalog.ProductImageRepository productImages,
                              com.specskart.spin.SpinService spins) {
        this.spins = spins;
        this.nudges = nudges;
        this.productImages = productImages;
        this.faqs = faqs;
        this.push = push;
        this.orderQuery = orderQuery;
        this.lensInquiries = lensInquiries;
        this.memberships = memberships;
        this.provider = provider;
        this.messages = messages;
        this.frameFinder = frameFinder;
        this.leadService = leadService;
        this.analytics = analytics;
        this.props = props;
        this.catalog = catalog;
        this.carts = carts;
        this.followUp = followUp;
        this.reviewCapture = reviewCapture;
    }

    /** How a customer's emoji reaction reads in the chat: "Reacted 👍". */
    static final String REACTION = "Reacted ";

    @Transactional
    public void handleInbound(Lead lead, String text, String buttonId, String waMessageId) {
        handleInbound(lead, text, buttonId, waMessageId, null, null);
    }

    @Transactional
    public void handleInbound(Lead lead, String text, String buttonId, String waMessageId, String mediaId, String mediaName) {
        // the filename is only logged for the chat -- never fed to the bot/opt-out matching below
        logInbound(lead.getId(), waMessageId, mediaName != null ? mediaName : text, buttonId, mediaId);
        // a 👍 on our message is not a question: staff see it, but the bot doesn't answer it and it
        // doesn't count as a customer waiting for a reply
        if (buttonId == null && mediaId == null && text != null && text.startsWith(REACTION)) {
            String who = lead.getName() != null && !lead.getName().isBlank() ? lead.getName() : "+" + waId(lead);
            String emoji = text.substring(REACTION.length()).split(" to: ", 2)[0];
            push.notifyStaff(who + " reacted " + emoji, text, "/chat/" + waId(lead));
            return;
        }
        // opt-out keyword (answered there -- a menu after "STOP" reads as ignoring it), or defer the automated touches
        if (followUp.onInbound(lead.getId(), text)) return;
        nudges.customerWrote(lead.getId());

        // Nobody is watching the chat screen all day: tell the staff phones someone wrote in, so they
        // can take over from the bot. A menu-button tap is the bot's own conversation -- not pushed,
        // or browsing the menu would buzz every phone -- unless a staff chat is open.
        boolean staffChat = lead.getAgentChatUntil() != null && lead.getAgentChatUntil().isAfter(java.time.Instant.now());
        if (staffChat || buttonId == null) {
            String who = lead.getName() != null && !lead.getName().isBlank() ? lead.getName() : "+" + waId(lead);
            String said = text == null || text.isBlank() ? "(sent a photo or file)" : text;
            push.notifyStaff((staffChat ? "Reply from " : "Message from ") + who,
                    said.length() > 200 ? said.substring(0, 200) + "…" : said, "/chat/" + waId(lead));
        }

        // A staff member is talking to them from the POS chat: the reply is for that person, not
        // for the menu. Logged above, so it shows up in the chat.
        if (staffChat) {
            log.info("bot quiet for lead {} (staff chat until {})", lead.getId(), lead.getAgentChatUntil());
            nudges.awaitingStaff(lead.getId());
            return;
        }

        // A bare "1".."5" only means a star rating when a post-purchase ask is pending —
        // otherwise it falls through to the ordinary welcome-menu shortcuts below.
        if (buttonId == null && text != null && text.trim().matches("[1-5]")
                && reviewCapture.awaitingRating(lead)) {
            handleReview(lead, Integer.parseInt(text.trim()));
            return;
        }

        // "BUY 1/2/3" against the picks just shown — a freeform reply, not a button, and
        // its meaning depends on what was last sent, so it's resolved before classify().
        if (buttonId == null && text != null) {
            var m = BUY_PATTERN.matcher(text.trim());
            if (m.matches()) { handleBuy(lead, Integer.parseInt(m.group(1))); return; }
        }

        // A tapped product row names its own product, so it doesn't depend on what was last sent.
        if (buttonId != null && buttonId.startsWith(ROW_BUY_PREFIX)) {
            handleBuySlug(lead, buttonId.substring(ROW_BUY_PREFIX.length()));
            return;
        }

        // Same for a tapped order row from the "which order did you mean?" list.
        if (buttonId != null && buttonId.startsWith(ROW_TRACK_PREFIX)) {
            handleTrackRow(lead, buttonId);
            return;
        }

        if (buttonId != null && buttonId.startsWith(ROW_FAQ_PREFIX)) {
            handleFaqRow(lead, buttonId.substring(ROW_FAQ_PREFIX.length()));
            return;
        }
        if (buttonId != null && buttonId.startsWith(LeadNudges.ROW_BOOK_PREFIX)) {
            handleBook(lead, buttonId.substring(LeadNudges.ROW_BOOK_PREFIX.length()));
            return;
        }
        if (buttonId != null && buttonId.startsWith(ROW_FAQ_MORE_PREFIX)) {
            sendFaqs(lead, Integer.parseInt(buttonId.substring(ROW_FAQ_MORE_PREFIX.length())));
            return;
        }

        if (buttonId == null) {
            // A photo is nearly always a prescription (or the frame they want): a person has to look.
            if (mediaId != null) { sendHandoff(lead, true); return; }
            java.time.LocalDate asked = text == null ? null : requestedDay(text.toLowerCase(), lusakaNow().toLocalDate());
            if (asked != null && VISIT_WORDS.matcher(text.toLowerCase()).find()) { sendDayAnswer(lead, asked); return; }
            Optional<Faq> faq = faqFor(text);
            if (faq.isPresent()) { sendFaqAnswer(lead, faq.get(), false); return; }
        }

        BotIntent intent = classify(text, buttonId);
        log.info("bot intent {} for lead {}", intent, lead.getId());
        switch (intent) {
            case FIND_FRAMES -> sendFrameFinderLink(lead);
            // The client's frames flow (2026-10-01): any frames question -> "want to know what
            // suits you?" -> Yes: the face-shape chart / Explore: the website with its shape +
            // price filters. A price question gets the range and the shop's address.
            case FRAMES, HELP_CHOOSE -> sendSuitsPrompt(lead);
            case FRAME_PHOTOS -> sendFramePhotos(lead);
            case SPIN -> sendText(lead, spins.offer(lead));
            case BOOK_VISIT -> sendBookingDays(lead);
            // budget buttons still sitting in older chats keep working
            case BUDGET_LOW -> sendBudgetPicks(lead, "low");
            case BUDGET_MED -> sendBudgetPicks(lead, "mid");
            case BUDGET_HIGH -> sendBudgetPicks(lead, "high");
            case SUITS_YOU -> sendFaceChart(lead);
            case EXPLORE_FRAMES -> sendLink(lead, "Browse all our frames here — filter by shape and price:\n"
                    + props.frontendBaseUrl() + "/store");
            case PRICE -> sendPrice(lead, text);
            case VISIT_WEBSITE -> sendLink(lead, "Here's our website: " + props.frontendBaseUrl());
            case EXPLORE_LENS -> sendLensLink(lead);
            case TRACK_ORDER -> sendOrderStatus(lead);
            case CARE -> sendCare(lead);
            case FAQ -> sendFaqs(lead, 0);
            case RESULTS_SHOW_FRAMES -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.INTERESTED);
                sendShop(lead, "Here are frames for you — filter by shape and price:\n");
            }
            case RESULTS_NOT_NOW -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.FOLLOW_UP);
                followUp.enroll(lead.getId());
                sendText(lead, "No problem — your recommendations are saved. Message us anytime to pick up where you left off.");
            }
            case MENU -> sendMenu(lead);
            case MORE_INFO -> sendIntro(lead);
            // A second "hi" or an off-script message soon after a menu: another menu is what made
            // people feel ignored -- tell them a person is coming instead (staff are pushed above).
            case GREETING -> { if (menuSentRecently(lead)) sendHandoff(lead); else sendWelcome(lead); }
            case UNKNOWN -> { if (menuSentRecently(lead)) sendHandoff(lead); else sendMenu(lead); }
        }
    }

    // ponytail: frames welcome buttons (FIND/EXPLORE/HELP_CHOOSE) are unused below while the
    // client's lens-only funnel is live — their handlers stay wired in handleInbound/classify
    // so re-enabling this is a one-line change, not a rebuild.
    public void sendWelcome(Lead lead) {
        String name = lead.getName() != null ? " " + lead.getName().split(" ")[0] : "";
        List<WhatsAppProvider.Button> buttons = new ArrayList<>();
        // The free eye test is what gets people through the door, so it leads (Meta allows 3 buttons).
        buttons.add(new WhatsAppProvider.Button(LeadNudges.BTN_BOOK, "Free eye test 👁️"));
        // Only worth a button to someone who has actually ordered — a fresh lead has nothing to track.
        buttons.add(latestOrderUpdate(lead).isPresent()
                ? new WhatsAppProvider.Button(BTN_TRACK_ORDER, "Track my order 📦")
                : new WhatsAppProvider.Button(BTN_EXPLORE_LENS, "Explore Lens 🔍"));
        buttons.add(new WhatsAppProvider.Button(BTN_FAQ, "FAQs ❓"));
        provider.sendButtons(waId(lead),
                "Hi" + name + " 👋\nWelcome to " + props.storeName() + ".", buttons);
        logOutbound(lead.getId(), "interactive", "welcome");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    /**
     * The top-level menu, as a list rather than buttons so each option can carry a description.
     * Rows cover only what the client actually has switched on — the frames funnel stays out of
     * here for the same reason it is off the welcome message (see sendWelcome).
     */
    private void sendMenu(Lead lead) {
        provider.sendList(waId(lead), "What can I help you with?", "Choose an option", menuRows());
        logOutbound(lead.getId(), "interactive", "menu");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private List<WhatsAppProvider.Row> menuRows() {
        List<WhatsAppProvider.Row> rows = new ArrayList<>(List.of(
                new WhatsAppProvider.Row(BTN_SPIN, "Spin & win a prize",
                        "Every spin wins. One spin per customer"),
                new WhatsAppProvider.Row(LeadNudges.BTN_BOOK, "Book a free eye test",
                        "Pick a day and we'll expect you"),
                new WhatsAppProvider.Row(BTN_FRAME_PHOTOS, "See our frames",
                        "Photos and prices, right here in the chat"),
                new WhatsAppProvider.Row(BTN_EXPLORE_LENS, "Lenses",
                        "Clear or photochromatic, with or without blue-block"),
                new WhatsAppProvider.Row(BTN_TRACK_ORDER, "Track my order",
                        "See where your order has got to"),
                new WhatsAppProvider.Row(BTN_FAQ, "FAQs",
                        "Prices, insurance, opening hours, location and more")));
        // Compile-time gate: with the feature off there is no row, so a client who never bought
        // into memberships ships a build where customers are never offered one.
        if (MembershipService.ENABLED) {
            rows.add(new WhatsAppProvider.Row(BTN_CARE, MembershipService.NAME,
                    MembershipService.DISCOUNT_PERCENT + "% off every pair, all year"));
        }
        rows.add(new WhatsAppProvider.Row(BTN_WEBSITE, "Our website",
                "Browse " + props.storeName() + " online"));
        return rows;
    }

    /**
     * Specskart Care: tell an existing member what they have, or sell it. The checkout link is
     * the ordinary Flutterwave hop the lens funnel already uses, so there is no new payment path.
     */
    private void sendCare(Lead lead) {
        if (!MembershipService.ENABLED) { sendMenu(lead); return; }

        Membership mine = memberships.activeFor(lead.getId());
        if (mine != null) {
            sendText(lead, "You're a " + MembershipService.NAME + " member ✅\n\n"
                    + mine.getDiscountPercent() + "% comes off every lens order automatically — "
                    + "nothing to enter.\n\nValid until "
                    + OrderNotificationService.STAFF_TIME.format(mine.getExpiresAt()) + ".");
            return;
        }
        try {
            var purchase = memberships.startPurchase(lead.getId());
            sendText(lead, "✨ *" + MembershipService.NAME + "*\n\n"
                    + OrderNotificationService.money(purchase.priceMinor(), purchase.currency())
                    + " for a year, and every lens order is "
                    + MembershipService.DISCOUNT_PERCENT + "% cheaper — it pays for itself on your "
                    + "second pair.\n\n• " + MembershipService.DISCOUNT_PERCENT + "% off all lenses, "
                    + "applied automatically\n• Free adjustments whenever you need them\n"
                    + "• A yearly eye-test reminder so your prescription never goes stale\n\n"
                    + "Join here:\n" + purchase.checkoutUrl());
        } catch (ApiException e) {
            sendText(lead, "I couldn't start that just now — please try again in a moment.");
            log.warn("membership purchase failed for lead {}: {}", lead.getId(), e.getMessage());
        }
    }

    /**
     * "Where's my order?" — the highest-frequency post-purchase question, and until now it just
     * replayed the welcome menu. Answers from whichever is newer: a frames order or a lens order.
     */
    /**
     * "Track my order", answered by asking which one when there is more than one.
     *
     * It used to reply with whichever order was newest. That is right for a customer with one
     * order and wrong for anyone who has bought twice: they asked about the older one, were
     * told about the newer one, and had no way to say otherwise.
     */
    private void sendOrderStatus(Lead lead) {
        // Meta caps a list at ten rows and rejects the whole send on an overrun.
        List<TrackOption> options = Stream.concat(
                        orderQuery.optionsForLead(lead.getId()).stream(),
                        lensInquiries.optionsForLead(lead.getId()).stream())
                .sorted(Comparator.comparing(TrackOption::at).reversed())
                .limit(10)
                .toList();

        if (options.isEmpty()) {
            sendLink(lead, "I can't find an order under this number yet. "
                    + "Configure your lenses here and I'll keep you posted at every step:\n"
                    + lensInquiries.personalLink(lead));
            return;
        }
        // One order needs no menu -- asking someone to pick from a list of one is a worse
        // answer than simply answering.
        if (options.size() == 1) {
            sendText(lead, options.get(0).message());
            return;
        }
        provider.sendList(waId(lead), "Which order did you mean?", "My orders",
                options.stream()
                        .map(o -> new WhatsAppProvider.Row(o.rowId(), o.title(), o.description()))
                        .toList());
        logOutbound(lead.getId(), "interactive", "orders");
    }

    /** A tapped order row names its own order, so it does not depend on what was last sent. */
    private void handleTrackRow(Lead lead, String rowId) {
        Stream.concat(orderQuery.optionsForLead(lead.getId()).stream(),
                        lensInquiries.optionsForLead(lead.getId()).stream())
                .filter(o -> o.rowId().equals(rowId))
                .findFirst()
                .ifPresentOrElse(o -> sendText(lead, o.message()),
                        () -> sendText(lead, "I couldn't find that order any more — reply "
                                + "\"track my order\" and I'll list them again."));
    }

    /** The lead's most recent order across both funnels. */
    private Optional<TrackUpdate> latestOrderUpdate(Lead lead) {
        return Stream.of(orderQuery.latestForLead(lead.getId()), lensInquiries.latestForLead(lead.getId()))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(TrackUpdate::at));
    }

    /**
     * The admin-edited FAQ list. Meta caps a list at ten rows, so past ten the last row
     * becomes "More questions" and pages on.
     */
    private void sendFaqs(Lead lead, int offset) {
        List<Faq> all = faqs.findAllByOrderBySortOrderAscCreatedAtAsc();
        if (all.isEmpty() || offset >= all.size()) { sendMenu(lead); return; }
        List<Faq> rest = all.subList(offset, all.size());
        boolean more = rest.size() > 10;
        List<WhatsAppProvider.Row> rows = new ArrayList<>(rest.stream().limit(more ? 9 : 10)
                .map(f -> new WhatsAppProvider.Row(ROW_FAQ_PREFIX + f.getId(), f.getTitle(), f.getQuestion()))
                .toList());
        if (more) rows.add(new WhatsAppProvider.Row(ROW_FAQ_MORE_PREFIX + (offset + 9), "More questions ➡️", null));
        provider.sendList(waId(lead), "Frequently asked questions — tap one for the answer.", "See questions", rows);
        logOutbound(lead.getId(), "interactive", "faqs");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private void handleFaqRow(Lead lead, String id) {
        Optional<Faq> faq;
        try { faq = faqs.findById(UUID.fromString(id)); } catch (IllegalArgumentException e) { faq = Optional.empty(); }
        faq.ifPresentOrElse(f -> sendFaqAnswer(lead, f, true),
                () -> sendFaqs(lead, 0)); // deleted since the list went out
    }

    /** An FAQ answer. Opening hours lead with open/closed right now; the eye test offers to book it. */
    private void sendFaqAnswer(Lead lead, Faq f, boolean withQuestion) {
        boolean hours = f.getTitle().equalsIgnoreCase("Opening hours");
        String text = (withQuestion ? "*" + f.getQuestion() + "*\n\n" : "")
                + (hours ? openNowLine(lusakaNow()) + "\n\n" : "") + f.getAnswer();
        if (!f.getTitle().equalsIgnoreCase("Eye check-up")) { sendText(lead, text); return; }
        provider.sendButtons(waId(lead), text, List.of(new WhatsAppProvider.Button(LeadNudges.BTN_BOOK, "Book my visit")));
        logOutbound(lead.getId(), "interactive", text);
    }

    /**
     * "Book my visit": the next three days the shop is open. Today only while there's at least an
     * hour left, so nobody books a 16:55 slot they can't make.
     */
    private void sendBookingDays(Lead lead) {
        sendBookingDays(lead, lusakaNow().toLocalDate(), "When would you like to come in for your free eye check-up? 👁️\n"
                + "Pick a day and we'll expect you. There's no fee and no fixed time: come any time we're open.");
    }

    /**
     * "Can I come on 1 Jan?" / "are you open on Saturday?": that day's answer, and day buttons
     * starting from it -- so a holiday or a Sunday is said out loud, with the next open days offered.
     */
    private void sendDayAnswer(Lead lead, java.time.LocalDate day) {
        var today = lusakaNow().toLocalDate();
        if (day.isBefore(today)) day = today;
        String name = LeadNudges.DAY.format(day) + (day.getYear() != today.getYear() ? " " + day.getYear() : "");
        String holiday = HOLIDAYS.get(day);
        String body = day.equals(today)
                ? openNowLine(lusakaNow()) + "\nTap a day and we'll expect you for your free eye check-up 👁️"
                : closingHour(day) == 0
                ? "🔴 We're closed on " + name + (holiday != null ? " (" + holiday + ")" : "") + ".\n"
                        + "The next days we're open are below. Tap one and we'll expect you for your free eye check-up 👁️"
                : "🟢 Yes, we're open on " + name + ", " + OPENS_AT + ":00–" + closingHour(day) + ":00.\n"
                        + "Tap the day and we'll expect you for your free eye check-up 👁️";
        sendBookingDays(lead, day, body);
    }

    private void sendBookingDays(Lead lead, java.time.LocalDate from, String body) {
        provider.sendButtons(waId(lead), body, bookingDayButtons(from, lusakaNow()));
        logOutbound(lead.getId(), "interactive", "booking-days");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    /** The next three open days from {@code from}; today only while there's an hour left. */
    static List<WhatsAppProvider.Button> bookingDayButtons(java.time.LocalDate from, java.time.ZonedDateTime now) {
        List<WhatsAppProvider.Button> days = new ArrayList<>();
        for (int i = 0; i < 14 && days.size() < 3; i++) {
            java.time.LocalDate day = from.plusDays(i);
            int close = closingHour(day);
            if (close == 0 || (day.equals(now.toLocalDate()) && now.getHour() >= close - 1)) continue;
            long ahead = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), day);
            String label = ahead == 0 ? "Today" : ahead == 1 ? "Tomorrow"
                    : java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH).format(day);
            days.add(new WhatsAppProvider.Button(LeadNudges.ROW_BOOK_PREFIX + day, label));
        }
        return days;
    }

    private void handleBook(Lead lead, String isoDay) {
        java.time.LocalDate day;
        try { day = java.time.LocalDate.parse(isoDay); } catch (java.time.format.DateTimeParseException e) { day = null; }
        // an old button from last week, or a holiday since: offer fresh days rather than book a closed one
        if (day == null || day.isBefore(lusakaNow().toLocalDate()) || closingHour(day) == 0) {
            sendBookingDays(lead);
            return;
        }
        nudges.book(lead.getId(), day);
        leadService.advanceStatusSoft(lead.getId(), LeadStatus.INTERESTED);
        boolean today = day.equals(lusakaNow().toLocalDate());
        String shops = lensInquiries.shopLines();
        sendText(lead, "✅ You're booked for a free eye check-up " + (today ? "today" : "on " + LeadNudges.DAY.format(day)) + ".\n"
                + "We're open " + OPENS_AT + ":00–" + closingHour(day) + ":00 that day. Come any time and "
                + "bring your old glasses or prescription if you have them."
                + (shops.isBlank() ? "" : "\n\n" + shops)
                + (today ? "" : "\n\nWe'll send you a reminder that morning."));
    }

    /**
     * "Can you share frames on WhatsApp?" -- asked by leads who then went quiet when sent a website.
     * A few real frames from the catalogue, photo + price, then the way into the shop. Only frames
     * with a photo Meta can fetch; the web stock count isn't kept up from the till, so it isn't a filter.
     */
    private void sendFramePhotos(Lead lead) {
        if (recentOutbound(lead).stream().anyMatch(m -> "frame-photos".equals(m.getBody()))) { sendHandoff(lead); return; }
        int sent = 0;
        for (Product p : catalog.trending(12)) {
            if (sent == FRAME_PHOTOS || !"FRAME".equals(p.getKind())) continue;
            String url = productImages.findByProductIdOrderBySortAsc(p.getId()).stream()
                    .map(i -> props.whatsapp().absoluteAsset(i.getUrl())).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            if (url == null) continue;
            try {
                provider.sendImage(waId(lead), url, p.getName() + " · " + kwacha(p.getPriceMinor()));
                logOutbound(lead.getId(), "image", "frame-photo:" + p.getSlug());
                sent++;
            } catch (Exception e) {
                log.warn("frame photo {} failed for lead {}: {}", p.getSlug(), lead.getId(), e.getMessage());
            }
        }
        if (sent == 0) { sendVisitStore(lead); return; }
        provider.sendButtons(waId(lead), "A few of our frames 👆 There are many more in the shop, from K300. "
                        + "Come and try them on: the eye check-up is free.",
                List.of(new WhatsAppProvider.Button(LeadNudges.BTN_BOOK, "Book my visit"),
                        new WhatsAppProvider.Button(BTN_EXPLORE, "See more online")));
        logOutbound(lead.getId(), "interactive", "frame-photos");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private void sendLensLink(Lead lead) {
        sendLink(lead, "Take a look and configure your lenses here — clear or photochromatic, "
                + "with or without blue-block:\n" + lensInquiries.personalLink(lead));
    }

    private void sendBudgetPicks(Lead lead, String tier) {
        leadService.saveStyleProfile(lead.getId(), null, null, tier, null);
        List<Product> picks = catalog.forBudget(lead.getFaceShape(), tier, 3);
        if (picks.isEmpty()) {
            sendLink(lead, "Nothing in that range right now — here's the full shop:\n" + shopLink(lead));
            return;
        }
        leadService.rememberPicks(lead.getId(), picks.stream().map(Product::getSlug).toList());
        sendPicks(lead, "Here's what I'd pick for you 👓\nTap one and I'll add it to your bag.", picks);
    }

    /** Products as tappable list rows — no more asking the customer to type "BUY 2". The
     *  numeric reply still works for anyone mid-conversation (see BUY_PATTERN). */
    private void sendPicks(Lead lead, String body, List<Product> picks) {
        List<WhatsAppProvider.Row> rows = picks.stream()
                .map(p -> new WhatsAppProvider.Row(ROW_BUY_PREFIX + p.getSlug(), p.getName(),
                        OrderNotificationService.money(p.getPriceMinor(), p.getCurrency())))
                .toList();
        provider.sendList(waId(lead), body, "See the picks", rows);
        logOutbound(lead.getId(), "interactive", "picks");
        analytics.record(LeadEventType.PRODUCTS_SHOWN, lead.getId(), null);
    }

    /** A star rating in reply to the post-purchase ask — applied to every product in that order. */
    private void handleReview(Lead lead, int rating) {
        int recorded = reviewCapture.recordAndClear(lead.getId(), rating);
        String stars = "⭐".repeat(rating);
        String thanks = recorded > 0
                ? "Thanks for the " + stars + " rating! It helps other shoppers choose with confidence."
                : "Thanks for the rating!";
        sendText(lead, thanks);
    }

    /** Resolve a "BUY n" reply against the picks last shown, add it to a fresh lead-linked
     *  cart, and hand back a checkout link — the whole purchase never leaves the chat. */
    private void handleBuy(Lead lead, int index) {
        Object raw = lead.getProviderMetadata().get("lastPicks");
        List<?> slugs = raw instanceof List<?> l ? l : List.of();
        if (index < 1 || index > slugs.size()) {
            sendText(lead, "I don't have a pick #" + index + " for you right now — reply *Help me choose* to see options.");
            return;
        }
        handleBuySlug(lead, String.valueOf(slugs.get(index - 1)));
    }

    /** Add one named product to a fresh lead-linked cart and hand back a checkout link. */
    private void handleBuySlug(Lead lead, String slug) {
        var product = catalog.bySlug(slug);
        if (product.isEmpty()) {
            sendText(lead, "That one's no longer available — reply *Help me choose* and I'll find something else.");
            return;
        }
        String token = carts.startForLead(lead.getId()).getToken();
        carts.addItem(token, product.get().getId(), 1);
        String link = props.frontendBaseUrl() + "/store?c=" + token;
        sendText(lead, "Added *" + product.get().getName() + "* to your bag ✅\n\n"
                + "Checkout here — you collect and pay at our shop:\n" + link);
        analytics.record(LeadEventType.PRODUCTS_SHOWN, lead.getId(), null);
    }

    private void sendFrameFinderLink(Lead lead) {
        var created = frameFinder.createForLead(lead.getId(), lead.getCampaignId());
        leadService.advanceStatusSoft(lead.getId(), LeadStatus.ENGAGED);
        provider.sendText(waId(lead),
                "Let's find frames that match your face 👓\nTap below to start your free face analysis:\n"
                        + created.url());
        logOutbound(lead.getId(), "text", "frame-finder-link");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    /** Called after face analysis completes. Includes the lead's personal, time-limited discount. */
    @Transactional
    public void sendAnalysisFollowUp(Lead lead, String faceShapeDisplay, List<String> recommended,
                                     com.specskart.catalog.PromoCode promo) {
        String offer = promo == null ? "" : "\n\n🎁 " + promo.getDiscountValue() + "% off your first pair — code *"
                + promo.getCode() + "*"
                + (promo.getExpiresAt() != null ? " (next " + hoursLeft(promo) + "h only)" : "");
        String body = "Your frame analysis is ready 🎯\n\nFace match: " + faceShapeDisplay
                + "\n\nFrames we recommend:\n"
                + recommended.stream().map(r -> "• " + r).reduce((a, b) -> a + "\n" + b).orElse("")
                + offer
                + "\n\nWould you like to see matching frames?";
        provider.sendButtons(waId(lead), body,
                List.of(new WhatsAppProvider.Button(BTN_RESULTS_FRAMES, "Show Me Frames"),
                        new WhatsAppProvider.Button(BTN_RESULTS_NOT_NOW, "Not Now")));
        logOutbound(lead.getId(), "interactive", "analysis-follow-up");
        analytics.record(LeadEventType.WHATSAPP_RESULTS_REQUESTED, lead.getId(), null);
        followUp.enroll(lead.getId()); // start the nurture clock now that they've been analysed
    }

    private static long hoursLeft(com.specskart.catalog.PromoCode promo) {
        long h = java.time.Duration.between(java.time.Instant.now(), promo.getExpiresAt()).toHours();
        return Math.max(1, h);
    }

    /**
     * Agent-initiated re-engagement of a cold lead. Goes out as an approved template
     * because that is the only message type Meta delivers outside the 24-hour window.
     * Template params: {{1}} = lead first name, {{2}} = store name.
     */
    @Transactional
    public void sendManualFollowUp(Lead lead) {
        if (!props.whatsapp().followUpConfigured()) {
            throw ApiException.badRequest("FOLLOW_UP_TEMPLATE_NOT_CONFIGURED",
                    "No WhatsApp re-engagement template is configured (set WHATSAPP_FOLLOW_UP_TEMPLATE).");
        }
        String firstName = lead.getName() != null && !lead.getName().isBlank()
                ? lead.getName().split(" ")[0] : "there";
        provider.sendTemplate(waId(lead), props.whatsapp().followUpTemplate(),
                props.whatsapp().followUpTemplateLang(), List.of(firstName, props.storeName()));
        leadService.advanceStatusSoft(lead.getId(), LeadStatus.FOLLOW_UP);
        logOutbound(lead.getId(), "template", props.whatsapp().followUpTemplate());
        analytics.record(LeadEventType.WHATSAPP_FOLLOW_UP_SENT, lead.getId(), null);
    }

    static BotIntent classify(String text, String buttonId) {
        if (buttonId != null) {
            try {
                return switch (buttonId) {
                    case BTN_FIND -> BotIntent.FIND_FRAMES;
                    case BTN_EXPLORE -> BotIntent.EXPLORE_FRAMES;
                    case BTN_WEBSITE -> BotIntent.VISIT_WEBSITE;
                    case BTN_RESULTS_FRAMES -> BotIntent.RESULTS_SHOW_FRAMES;
                    case BTN_RESULTS_NOT_NOW -> BotIntent.RESULTS_NOT_NOW;
                    case BTN_HELP_CHOOSE -> BotIntent.HELP_CHOOSE;
                    case BTN_EXPLORE_LENS -> BotIntent.EXPLORE_LENS;
                    case BTN_TRACK_ORDER -> BotIntent.TRACK_ORDER;
                    case BTN_MENU -> BotIntent.MENU;
                    case BTN_CARE -> BotIntent.CARE;
                    case BTN_FAQ -> BotIntent.FAQ;
                    case LeadNudges.BTN_BOOK -> BotIntent.BOOK_VISIT;
                    case BTN_FRAME_PHOTOS -> BotIntent.FRAME_PHOTOS;
                    case BTN_SPIN -> BotIntent.SPIN;
                    case BTN_SUITS -> BotIntent.SUITS_YOU;
                    case BTN_BUDGET_LOW -> BotIntent.BUDGET_LOW;
                    case BTN_BUDGET_MED -> BotIntent.BUDGET_MED;
                    case BTN_BUDGET_HIGH -> BotIntent.BUDGET_HIGH;
                    default -> BotIntent.UNKNOWN;
                };
            } catch (Exception ignored) { }
        }
        String t = text == null ? "" : text.toLowerCase().trim();
        if (t.isBlank()) return BotIntent.GREETING;
        if (t.matches(".*\\b(spin|prize)\\b.*")) return BotIntent.SPIN;
        if (t.matches(".*(hi|hello|hey|start|namaste).*") && t.length() < 15) return BotIntent.GREETING;
        // "can you send pics of your specs" -- before MORE_INFO, the ad prefill often rides along with it
        if ((t.matches(".*\\b(pics?|pictures?|photos?|images?|catalog(ue)?|share)\\b.*")
                        || t.matches(".*\\b(see|show)\\b.*\\b(frames?|specs|glasses|spectacles)\\b.*"))
                && !t.matches(".*\\b(prescription|rx)\\b.*")
                && !LENS_WORDS.matcher(t).find()) return BotIntent.FRAME_PHOTOS; // "photo chromatic"
        if (t.contains("information") || t.contains("more info") || t.contains("details")) return BotIntent.MORE_INFO;
        if (t.contains("menu") || t.contains("options")) return BotIntent.MENU;
        if (t.matches(".*\\b(book|booking|appointment)\\b.*")) return BotIntent.BOOK_VISIT;
        if (t.contains("faq") || t.contains("question")) return BotIntent.FAQ;
        if (MembershipService.ENABLED && (t.contains("care") || t.contains("member"))) return BotIntent.CARE;
        // Before the lens check on purpose — "where is my lens order" is a tracking question.
        // A bare "where" used to land here, so "where are you located?" got "I can't find an order".
        if (t.contains("track") || t.contains("my order") || t.matches(".*where('?s| is) my.*")
                || t.contains("order status")) return BotIntent.TRACK_ORDER;
        // Before the lens check: "how much are your lenses?" wants a price, not the configurator.
        // A typed prescription ("-6.00") is a price question too.
        if (t.matches(".*\\b(price|prices|pricing|cost|costs|how much|rate|rates|charges?|quot\\w*)\\b.*")
                || RX_NUMBER.matcher(t).find()) return BotIntent.PRICE;
        if (LENS_WORDS.matcher(t).find()) return BotIntent.EXPLORE_LENS;
        if (t.contains("help") || t.contains("choose") || t.contains("recommend") || t.contains("suggest")) return BotIntent.HELP_CHOOSE;
        if (t.matches(".*\\bfaces?\\b.*") || t.contains("suit") || t.contains("frame finder") || t.equals("1")) return BotIntent.FIND_FRAMES;
        if (t.contains("explore") || t.contains("latest") || t.equals("2")) return BotIntent.EXPLORE_FRAMES;
        if (t.contains("website") || t.contains("site") || t.equals("3")) return BotIntent.VISIT_WEBSITE;
        if (t.matches(".*\\b(frames?|spectacles?|specs|glasses|eyewear|sunglasses)\\b.*")) return BotIntent.FRAMES;
        return BotIntent.UNKNOWN;
    }

    private String waId(Lead lead) {
        return lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
    }

    /** A shop link on a cart pre-linked to this lead, so a purchase attributes back to the funnel. */
    private String shopLink(Lead lead) {
        String token = carts.startForLead(lead.getId()).getToken();
        String url = props.frontendBaseUrl() + "/store?c=" + token;
        if (lead.getFaceShape() != null) url += "&face=" + lead.getFaceShape();
        return url;
    }

    private void sendSuitsPrompt(Lead lead) {
        provider.sendButtons(waId(lead), "Would you like to know which frames suit your face? 👓",
                List.of(new WhatsAppProvider.Button(BTN_SUITS, "Yes, show me"),
                        new WhatsAppProvider.Button(BTN_FRAME_PHOTOS, "See photos 📸"),
                        new WhatsAppProvider.Button(BTN_EXPLORE, "Explore frames")));
        logOutbound(lead.getId(), "interactive", "suits-prompt");
    }

    /** The face-shape chart (frontend/public/face-shape-chart.jpg), then the way to the frames.
     *  If Meta can't fetch the image, the Frame Finder does the same job interactively. */
    private void sendFaceChart(Lead lead) {
        try {
            provider.sendImage(waId(lead), props.frontendBaseUrl() + "/face-shape-chart.jpg",
                    "Find your face shape, then pick a frame style that suits it.");
            logOutbound(lead.getId(), "image", "face-shape-chart");
        } catch (Exception e) {
            log.warn("face-shape chart send failed for lead {}: {}", lead.getId(), e.getMessage());
            sendFrameFinderLink(lead);
            return;
        }
        provider.sendButtons(waId(lead), "Found your shape? See the frames that match it:",
                List.of(new WhatsAppProvider.Button(BTN_EXPLORE, "Explore frames")));
    }

    /** The client's own words, then where the shop is (with a map) so they can walk in. */
    private void sendVisitStore(Lead lead) {
        String shops = lensInquiries.shopLines();
        sendText(lead, "We have a wide range of frames starting from K300 going up to K3,000. "
                + "Please visit our store to choose."
                + (shops.isBlank() ? "" : "\n\n" + shops));
    }

    private void sendShop(Lead lead, String intro) {
        sendLink(lead, intro + shopLink(lead));
    }

    /** Post-analysis "Show Me Frames": send the actual matched products with prices + a buy link. */
    private void sendRecommendedProducts(Lead lead) {
        String link = shopLink(lead);
        List<Product> picks = lead.getFaceShape() == null ? List.of()
                : catalog.forFaceShape(lead.getFaceShape(), 3);
        if (picks.isEmpty()) {
            sendText(lead, "Here are the frames matched to your face — shop them online:\n" + link);
            analytics.record(LeadEventType.PRODUCTS_SHOWN, lead.getId(), null);
            return;
        }
        leadService.rememberPicks(lead.getId(), picks.stream().map(Product::getSlug).toList());
        sendPicks(lead, "Frames matched to your face 👓\nTap one to add it to your bag.", picks);
        sendText(lead, "Or browse the full set & check out here:\n" + link);
    }

    /**
     * A link (configurator, website, shop) -- the same one four times in a row is what a customer
     * reads as "nobody is there", so a repeat within the window becomes the "a person will reply"
     * note. Answers to a question (FAQ, price, booking) go through sendText and are always sent.
     */
    private void sendLink(Lead lead, String text) {
        if (sentRecently(lead, text)) { sendHandoff(lead); return; }
        sendText(lead, text);
    }

    private void sendText(Lead lead, String text) {
        provider.sendText(waId(lead), text);
        logOutbound(lead.getId(), "text", text);
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private void sendHandoff(Lead lead) { sendHandoff(lead, false); }

    /** "A person will reply" -- once per window, then quiet until staff pick the chat up. */
    private void sendHandoff(Lead lead, boolean photo) {
        nudges.awaitingStaff(lead.getId()); // even when the note itself isn't repeated
        // A photo is always acknowledged (one per burst of photos); the plain note at most every 30 min.
        // Silence after a photo reads as "nobody is there" (2026-10-20 device test).
        java.time.Instant since = java.time.Instant.now().minus(photo ? PHOTO_ACK_WINDOW : HANDOFF_WINDOW);
        if (recentOutbound(lead).stream().anyMatch(m -> m.getBody() != null && m.getBody().contains(HANDOFF_MARK)
                && m.getCreatedAt().isAfter(since) && (!photo || m.getBody().startsWith("Got your photo")))) return;
        var now = lusakaNow();
        String text = (photo ? "Got your photo, thanks 📸 " : "Thanks! ")
                + (isOpen(now) ? "Someone " + HANDOFF_MARK + " here shortly 🙏"
                        : "We're closed right now, so someone " + HANDOFF_MARK + " when we open "
                        + nextOpening(now) + " at " + OPENS_AT + ":00 🙏");
        provider.sendText(waId(lead), text);
        logOutbound(lead.getId(), "text", text);
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private boolean sentRecently(Lead lead, String body) {
        String key = withoutQuery(body);
        return recentOutbound(lead).stream().anyMatch(m -> key.equals(withoutQuery(m.getBody())));
    }

    private boolean menuSentRecently(Lead lead) {
        return recentOutbound(lead).stream()
                .anyMatch(m -> List.of("menu", "welcome", "intro").contains(m.getBody()));
    }

    private List<WhatsAppMessage> recentOutbound(Lead lead) {
        return messages.findByLeadIdAndDirectionAndCreatedAtAfter(lead.getId(), "OUTBOUND",
                java.time.Instant.now().minus(REPEAT_WINDOW));
    }

    /** Personal links differ only in their signed query string -- the same message to a person. */
    static String withoutQuery(String s) {
        return s == null ? "" : s.replaceAll("\\?\\S*", "");
    }

    private Optional<Faq> faqFor(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String t = text.toLowerCase();
        return FAQ_KEYWORDS.stream().filter(e -> e.getKey().matcher(t).find()).findFirst()
                .flatMap(e -> faqs.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                        .filter(f -> f.getTitle().equalsIgnoreCase(e.getValue())).findFirst());
    }

    private String faqAnswer(String title, String fallback) {
        return faqs.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(f -> f.getTitle().equalsIgnoreCase(title)).map(Faq::getAnswer)
                .findFirst().orElse(fallback);
    }

    /**
     * A price question, answered in the chat. Frames keep the client's "range + visit the shop";
     * anything about lenses (or a typed prescription) gets the lens range and the ask that turns
     * it into a real quote -- the configurator link it used to get converted nobody.
     */
    private void sendPrice(Lead lead, String text) {
        nudges.quoted(lead.getId());
        String t = text == null ? "" : text.toLowerCase();
        boolean lens = LENS_WORDS.matcher(t).find() || RX_NUMBER.matcher(t).find();
        boolean frames = t.matches(".*\\b(frames?|spectacles?|specs|glasses|sunglasses)\\b.*");
        if (frames && !lens) { sendVisitStore(lead); return; }
        String power = lens ? typedPower(t) : null;
        if (power != null) {
            Map<String, Long> prices = lensInquiries.chatPrices(Math.abs(Double.parseDouble(power.replace(" ", ""))));
            if (!prices.isEmpty()) { sendLensQuote(lead, power, prices); return; }
        }
        String lensLine = faqAnswer("Lens prices", "Lenses start from K200, depending on your prescription.");
        String shops = lensInquiries.shopLines();
        sendText(lead, (lens ? lensLine
                        : faqAnswer("Frame prices", "Frames start at K300 and go up to K3,000.") + "\n" + lensLine)
                + "\n\n📸 Send a photo of your prescription here and we'll give you an exact price."
                + (shops.isBlank() ? "" : "\n\n" + shops));
    }

    /**
     * The one power in "how much for -2 lenses" / "colourmatic 0.5", or null when the question needs
     * the whole prescription (cylinder, reading add, bifocal...) -- those still get the photo ask.
     * Signed ("-2", "+1.25") or decimal ("0.5") only: a bare "2" is as likely "2 pairs".
     */
    static String typedPower(String t) {
        if (MULTIFOCAL_WORDS.matcher(t).find()) return null;
        var m = POWER.matcher(t);
        String best = null;
        double bestAbs = -1;
        while (m.find()) {
            if (m.group(1) == null && m.group(2).indexOf('.') < 0) continue;
            double v = Double.parseDouble(m.group(2));
            if (v > 20) continue;
            if (v > bestAbs) { bestAbs = v; best = (m.group(1) == null ? "" : m.group(1).trim()) + m.group(2); }
        }
        return best;
    }

    static final java.util.regex.Pattern VISIT_WORDS = java.util.regex.Pattern.compile(
            "visit|\\bcome\\b|coming|\\bopen\\b|book|appointment|drop in|pass by|available|working");
    private static final String MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec";
    private static final java.util.regex.Pattern DAY_MONTH = java.util.regex.Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:of\\s+)?(" + MONTHS + ")[a-z]*\\.?(?:,?\\s*(20\\d\\d))?");
    private static final java.util.regex.Pattern MONTH_DAY = java.util.regex.Pattern.compile(
            "\\b(" + MONTHS + ")[a-z]*\\.?\\s*(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s*(20\\d\\d))?");
    private static final java.util.regex.Pattern WEEKDAY = java.util.regex.Pattern.compile(
            "\\b(mon|tue|wed|thu|fri|sat|sun)(day|sday|nesday|rsday|urday)\\b|\\b(mon|tues?|wed|thurs?|fri|sat)\\b");

    /**
     * The day a customer names: "today", "tomorrow", "saturday", "1 jan", "january 1st 2027".
     * A date with no year that has passed means next year. Null when there's none.
     */
    static java.time.LocalDate requestedDay(String t, java.time.LocalDate today) {
        if (t.matches(".*\\btomorrow\\b.*")) return today.plusDays(1);
        if (t.matches(".*\\btoday\\b.*")) return today;
        java.util.regex.Matcher m = DAY_MONTH.matcher(t);
        Integer day = null, year = null;
        String month = null;
        if (m.find()) { day = Integer.valueOf(m.group(1)); month = m.group(2); year = m.group(3) == null ? null : Integer.valueOf(m.group(3)); }
        else if ((m = MONTH_DAY.matcher(t)).find()) { month = m.group(1); day = Integer.valueOf(m.group(2)); year = m.group(3) == null ? null : Integer.valueOf(m.group(3)); }
        if (month != null) {
            int mon = MONTHS.indexOf(month) / 4 + 1;
            try {
                java.time.LocalDate d = java.time.LocalDate.of(year != null ? year : today.getYear(), mon, day);
                return year == null && d.isBefore(today) ? d.plusYears(1) : d;
            } catch (java.time.DateTimeException badDate) { return null; }
        }
        m = WEEKDAY.matcher(t);
        if (m.find()) {
            String[] names = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
            String abbr = (m.group(1) != null ? m.group(1) : m.group(3)).substring(0, 3);
            java.time.DayOfWeek dow = java.time.DayOfWeek.of(java.util.Arrays.asList(names).indexOf(abbr) + 1);
            return today.with(java.time.temporal.TemporalAdjusters.nextOrSame(dow));
        }
        return null;
    }

    private void sendLensQuote(Lead lead, String power, Map<String, Long> prices) {
        StringBuilder sb = new StringBuilder("👓 Single-vision lenses for power " + power + " (price for a pair):\n");
        prices.forEach((label, minor) -> sb.append("• ").append(label).append(": ").append(kwacha(minor)).append('\n'));
        sb.append("\nFrames start from K300.\n")
                .append("Have cylinder (CYL) or reading (ADD) numbers too? 📸 Send a photo of your prescription "
                        + "and we'll confirm the exact price.");
        String shops = lensInquiries.shopLines();
        if (!shops.isBlank()) sb.append("\n\n").append(shops);
        sendText(lead, sb.toString());
    }

    static String kwacha(long minor) {
        return minor % 100 == 0 ? String.format("K%,d", minor / 100) : String.format("K%,.2f", minor / 100.0);
    }

    /**
     * "Are you open?", answered for right now in Lusaka -- also tells a customer writing at night
     * when the "someone will reply" promise will actually happen.
     * ponytail: hours mirror the FAQ and ignore public holidays; read them from the shop if that matters.
     */
    static String openNowLine(java.time.ZonedDateTime now) {
        if (isOpen(now)) return "🟢 We're open now, until " + closingHour(now.toLocalDate()) + ":00 today.";
        String holiday = HOLIDAYS.get(now.toLocalDate());
        return (holiday != null ? "🔴 We're closed today for " + holiday + "." : "🔴 We're closed right now.")
                + " We open " + nextOpening(now) + " at " + OPENS_AT + ":00.";
    }

    /** Opening hour every open day (Google Maps listing: 8:00). */
    static final int OPENS_AT = 8;

    /**
     * Public holidays the shop closes on (client's lists for 2026 and 2027, sent 2026-10-20).
     * ponytail: hardcoded -- add 2028's dates here when the client sends them.
     */
    static final Map<java.time.LocalDate, String> HOLIDAYS = Map.ofEntries(
            Map.entry(java.time.LocalDate.of(2026, 10, 24), "Independence Day"),
            Map.entry(java.time.LocalDate.of(2026, 12, 25), "Christmas"),
            Map.entry(java.time.LocalDate.of(2027, 1, 1), "New Year's Day"),
            Map.entry(java.time.LocalDate.of(2027, 3, 8), "International Women's Day"),
            Map.entry(java.time.LocalDate.of(2027, 3, 12), "Youth Day"),
            Map.entry(java.time.LocalDate.of(2027, 3, 26), "Good Friday"),
            Map.entry(java.time.LocalDate.of(2027, 3, 27), "Holy Saturday"),
            Map.entry(java.time.LocalDate.of(2027, 3, 29), "Easter Monday"),
            Map.entry(java.time.LocalDate.of(2027, 4, 28), "Kenneth Kaunda Day"),
            Map.entry(java.time.LocalDate.of(2027, 5, 1), "Labour Day"),
            Map.entry(java.time.LocalDate.of(2027, 5, 25), "African Freedom Day"),
            Map.entry(java.time.LocalDate.of(2027, 7, 5), "Heroes' Day"),
            Map.entry(java.time.LocalDate.of(2027, 7, 6), "Unity Day"),
            Map.entry(java.time.LocalDate.of(2027, 8, 2), "Farmers' Day"),
            Map.entry(java.time.LocalDate.of(2027, 10, 24), "Independence Day"),
            Map.entry(java.time.LocalDate.of(2027, 10, 25), "Independence Day (holiday in lieu)"),
            Map.entry(java.time.LocalDate.of(2027, 12, 25), "Christmas"));

    /** 17 weekdays, 14 Saturday, 0 = closed all day (Sunday or a holiday). */
    static int closingHour(java.time.LocalDate day) {
        if (HOLIDAYS.containsKey(day)) return 0;
        return switch (day.getDayOfWeek()) { case SUNDAY -> 0; case SATURDAY -> 14; default -> 17; };
    }

    static boolean isOpen(java.time.ZonedDateTime now) {
        return now.getHour() >= OPENS_AT && now.getHour() < closingHour(now.toLocalDate());
    }

    /** "today", "tomorrow" or "on Monday": the next day the shop opens, skipping Sundays and holidays. */
    static String nextOpening(java.time.ZonedDateTime now) {
        for (int i = 0; i < 14; i++) {
            java.time.LocalDate day = now.toLocalDate().plusDays(i);
            if (closingHour(day) == 0 || (i == 0 && now.getHour() >= OPENS_AT)) continue;
            return i == 0 ? "today" : i == 1 ? "tomorrow"
                    : "on " + day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        }
        return "soon";
    }

    static java.time.ZonedDateTime lusakaNow() {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Lusaka"));
    }

    /**
     * The ad's prefilled "Can I get more info on this?" -- the first thing almost every ad lead
     * sends. It used to get the lens configurator link, which answered none of what they asked
     * next (location, promo, eye test, price). The facts, then the menu, in one message.
     * ponytail: prices/hours here repeat the FAQs; build from the FAQ rows if the client edits them often.
     */
    private void sendIntro(Lead lead) {
        String name = lead.getName() != null && !lead.getName().isBlank() ? " " + lead.getName().split(" ")[0] : "";
        String shops = lensInquiries.shopLines();
        String body = "Hi" + name + " 👋 Thanks for contacting " + props.storeName() + "!\n\n"
                + "👁️ Eye check-up: FREE right now\n"
                + "👓 Frames: K300 – K3,000\n"
                + "🔍 Lenses: from K200, priced by your prescription\n"
                + "🏥 Insurance: Prudential, Onelife, ZISC\n"
                + "🕘 Mon–Fri 8:00–17:00, Sat 8:00–14:00\n"
                + (shops.isBlank() ? "" : "\n" + shops + "\n")
                + "\n📸 Send a photo of your prescription for an exact price, or ask us anything.";
        provider.sendList(waId(lead), body.length() > 1024 ? body.substring(0, 1024) : body,
                "Choose an option", menuRows());
        logOutbound(lead.getId(), "interactive", "intro");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private void logInbound(UUID leadId, String waMessageId, String text, String buttonId, String mediaId) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("INBOUND");
        m.setWaMessageId(waMessageId);
        // a photo/file arrives as text "[image]" / "[document]" -- that bracketed word is its type
        m.setMessageType(buttonId != null ? "button_reply" : mediaId != null ? mediaType(text) : "text");
        m.setMediaId(mediaId);
        m.setBody(buttonId != null ? "[" + buttonId + "] " + (text == null ? "" : text) : text);
        m.setStatus("received");
        messages.save(m);
    }

    /** "[document]" -> "document"; a filename (no brackets) is only ever a document's. */
    private static String mediaType(String text) {
        return text != null && text.startsWith("[") ? text.replace("[", "").replace("]", "") : "document";
    }

    private void logOutbound(UUID leadId, String type, String body) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType(type);
        m.setBody(body);
        m.setStatus("sent");
        messages.save(m);
    }
}
