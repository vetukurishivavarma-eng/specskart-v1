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
    /** A list row that buys one specific product: {@code BUY:<slug>}. */
    static final String ROW_BUY_PREFIX = "BUY:";
    /** A list row naming one of the customer's own orders: {@code TRACK:L:<uuid>} or {@code TRACK:O:<orderNo>}. */
    static final String ROW_TRACK_PREFIX = "TRACK:";
    static final String BTN_BUDGET_LOW = "BUDGET_LOW";
    static final String BTN_BUDGET_MED = "BUDGET_MED";
    static final String BTN_BUDGET_HIGH = "BUDGET_HIGH";

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

    public WhatsAppBotService(WhatsAppProvider provider, WhatsAppMessageRepository messages,
                              FrameFinderService frameFinder, LeadService leadService,
                              AnalyticsService analytics, AppProperties props,
                              CatalogService catalog, CartService carts, LeadFollowUpService followUp,
                              ReviewCaptureService reviewCapture, OrderQueryService orderQuery,
                              LensInquiryService lensInquiries, MembershipService memberships) {
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

    @Transactional
    public void handleInbound(Lead lead, String text, String buttonId, String waMessageId) {
        logInbound(lead.getId(), waMessageId, text, buttonId);
        followUp.onInbound(lead.getId(), text); // opt-out keyword, or defer the automated touches

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

        BotIntent intent = classify(text, buttonId);
        log.info("bot intent {} for lead {}", intent, lead.getId());
        switch (intent) {
            case FIND_FRAMES -> sendFrameFinderLink(lead);
            case EXPLORE_FRAMES -> sendShop(lead, "Our full collection is here — every style, buy online:\n");
            case VISIT_WEBSITE -> sendText(lead, "Here's our website: " + props.frontendBaseUrl());
            case EXPLORE_LENS -> sendLensLink(lead);
            case TRACK_ORDER -> sendOrderStatus(lead);
            case CARE -> sendCare(lead);
            case HELP_CHOOSE -> sendBudgetPrompt(lead);
            case BUDGET_LOW -> sendBudgetPicks(lead, "low");
            case BUDGET_MED -> sendBudgetPicks(lead, "mid");
            case BUDGET_HIGH -> sendBudgetPicks(lead, "high");
            case RESULTS_SHOW_FRAMES -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.INTERESTED);
                sendRecommendedProducts(lead);
            }
            case RESULTS_NOT_NOW -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.FOLLOW_UP);
                followUp.enroll(lead.getId());
                sendText(lead, "No problem — your recommendations are saved. Message us anytime to pick up where you left off.");
            }
            case MENU -> sendMenu(lead);
            case GREETING -> sendWelcome(lead);
            // Off-script: the full option list is a better dead-end than the welcome's one button.
            case UNKNOWN -> sendMenu(lead);
        }
    }

    // ponytail: frames welcome buttons (FIND/EXPLORE/HELP_CHOOSE) are unused below while the
    // client's lens-only funnel is live — their handlers stay wired in handleInbound/classify
    // so re-enabling this is a one-line change, not a rebuild.
    public void sendWelcome(Lead lead) {
        String name = lead.getName() != null ? " " + lead.getName().split(" ")[0] : "";
        List<WhatsAppProvider.Button> buttons = new ArrayList<>();
        buttons.add(new WhatsAppProvider.Button(BTN_EXPLORE_LENS, "Explore Lens 🔍"));
        // Only worth a button to someone who has actually ordered — a fresh lead has nothing to track.
        if (latestOrderUpdate(lead).isPresent()) {
            buttons.add(new WhatsAppProvider.Button(BTN_TRACK_ORDER, "Track my order 📦"));
        }
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
        List<WhatsAppProvider.Row> rows = new ArrayList<>(List.of(
                new WhatsAppProvider.Row(BTN_EXPLORE_LENS, "Lenses",
                        "Clear or photochromatic, with or without blue-block"),
                new WhatsAppProvider.Row(BTN_TRACK_ORDER, "Track my order",
                        "See where your order has got to")));
        // Compile-time gate: with the feature off there is no row, so a client who never bought
        // into memberships ships a build where customers are never offered one.
        if (MembershipService.ENABLED) {
            rows.add(new WhatsAppProvider.Row(BTN_CARE, MembershipService.NAME,
                    MembershipService.DISCOUNT_PERCENT + "% off every pair, all year"));
        }
        rows.add(new WhatsAppProvider.Row(BTN_WEBSITE, "Our website",
                "Browse " + props.storeName() + " online"));
        provider.sendList(waId(lead), "What can I help you with?", "Choose an option", rows);
        logOutbound(lead.getId(), "interactive", "menu");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
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
            sendText(lead, "I can't find an order under this number yet. "
                    + "Configure your lenses here and I'll keep you posted at every step:\n"
                    + props.frontendBaseUrl() + "/lens");
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

    private void sendLensLink(Lead lead) {
        sendText(lead, "Take a look and configure your lenses here — clear or photochromatic, "
                + "with or without blue-block:\n" + props.frontendBaseUrl() + "/lens");
    }

    /** Personal-shopper entry point: one question (budget), then picks — no LLM, just a
     *  short deterministic flow reusing the catalogue + recommendation logic. */
    private void sendBudgetPrompt(Lead lead) {
        provider.sendButtons(waId(lead), "Happy to help 🛍️ What's your budget for a pair?",
                List.of(new WhatsAppProvider.Button(BTN_BUDGET_LOW, "Under K300"),
                        new WhatsAppProvider.Button(BTN_BUDGET_MED, "K300 – K600"),
                        new WhatsAppProvider.Button(BTN_BUDGET_HIGH, "K600+")));
        logOutbound(lead.getId(), "interactive", "budget-prompt");
    }

    private void sendBudgetPicks(Lead lead, String tier) {
        leadService.saveStyleProfile(lead.getId(), null, null, tier, null);
        List<Product> picks = catalog.forBudget(lead.getFaceShape(), tier, 3);
        if (picks.isEmpty()) {
            sendText(lead, "Nothing in that range right now — here's the full shop:\n" + shopLink(lead));
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
                + "Checkout here — pay online or cash on delivery:\n" + link);
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

    BotIntent classify(String text, String buttonId) {
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
                    case BTN_BUDGET_LOW -> BotIntent.BUDGET_LOW;
                    case BTN_BUDGET_MED -> BotIntent.BUDGET_MED;
                    case BTN_BUDGET_HIGH -> BotIntent.BUDGET_HIGH;
                    default -> BotIntent.UNKNOWN;
                };
            } catch (Exception ignored) { }
        }
        String t = text == null ? "" : text.toLowerCase().trim();
        if (t.isBlank()) return BotIntent.GREETING;
        if (t.matches(".*(hi|hello|hey|start|namaste).*") && t.length() < 15) return BotIntent.GREETING;
        if (t.contains("menu") || t.contains("options")) return BotIntent.MENU;
        if (MembershipService.ENABLED && (t.contains("care") || t.contains("member"))) return BotIntent.CARE;
        // Before the lens check on purpose — "where is my lens order" is a tracking question.
        if (t.contains("track") || t.contains("where") || t.contains("my order")
                || t.contains("order status")) return BotIntent.TRACK_ORDER;
        if (t.contains("lens")) return BotIntent.EXPLORE_LENS;
        if (t.contains("help") || t.contains("choose") || t.contains("recommend") || t.contains("suggest")) return BotIntent.HELP_CHOOSE;
        if (t.contains("face") || t.contains("suit") || t.contains("frame finder") || t.equals("1")) return BotIntent.FIND_FRAMES;
        if (t.contains("explore") || t.contains("latest") || t.contains("catalog") || t.equals("2")) return BotIntent.EXPLORE_FRAMES;
        if (t.contains("website") || t.contains("site") || t.equals("3")) return BotIntent.VISIT_WEBSITE;
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

    private void sendShop(Lead lead, String intro) {
        sendText(lead, intro + shopLink(lead));
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

    private void sendText(Lead lead, String text) {
        provider.sendText(waId(lead), text);
        logOutbound(lead.getId(), "text", text);
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
    }

    private void logInbound(UUID leadId, String waMessageId, String text, String buttonId) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("INBOUND");
        m.setWaMessageId(waMessageId);
        m.setMessageType(buttonId != null ? "button_reply" : "text");
        m.setBody(buttonId != null ? "[" + buttonId + "] " + (text == null ? "" : text) : text);
        m.setStatus("received");
        messages.save(m);
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
