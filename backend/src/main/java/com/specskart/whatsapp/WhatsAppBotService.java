package com.specskart.whatsapp;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.CatalogService;
import com.specskart.catalog.Product;
import com.specskart.config.AppProperties;
import com.specskart.framefinder.FrameFinderService;
import com.specskart.order.CartService;
import com.specskart.order.OrderNotificationService;
import com.specskart.order.ReviewCaptureService;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadFollowUpService;
import com.specskart.lead.LeadService;
import com.specskart.lead.LeadStatus;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public WhatsAppBotService(WhatsAppProvider provider, WhatsAppMessageRepository messages,
                              FrameFinderService frameFinder, LeadService leadService,
                              AnalyticsService analytics, AppProperties props,
                              CatalogService catalog, CartService carts, LeadFollowUpService followUp,
                              ReviewCaptureService reviewCapture) {
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
                && reviewCapture.pendingOrderId(lead) != null) {
            handleReview(lead, Integer.parseInt(text.trim()));
            return;
        }

        // "BUY 1/2/3" against the picks just shown — a freeform reply, not a button, and
        // its meaning depends on what was last sent, so it's resolved before classify().
        if (buttonId == null && text != null) {
            var m = BUY_PATTERN.matcher(text.trim());
            if (m.matches()) { handleBuy(lead, Integer.parseInt(m.group(1))); return; }
        }

        BotIntent intent = classify(text, buttonId);
        log.info("bot intent {} for lead {}", intent, lead.getId());
        switch (intent) {
            case FIND_FRAMES -> sendFrameFinderLink(lead);
            case EXPLORE_FRAMES -> sendShop(lead, "Our full collection is here — every style, buy online:\n");
            case VISIT_WEBSITE -> sendText(lead, "Here's our website: " + props.frontendBaseUrl());
            case EXPLORE_LENS -> sendLensLink(lead);
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
            case GREETING, UNKNOWN -> sendWelcome(lead);
        }
    }

    // ponytail: frames welcome buttons (FIND/EXPLORE/HELP_CHOOSE) are unused below while the
    // client's lens-only funnel is live — their handlers stay wired in handleInbound/classify
    // so re-enabling this is a one-line change, not a rebuild.
    public void sendWelcome(Lead lead) {
        String name = lead.getName() != null ? " " + lead.getName().split(" ")[0] : "";
        provider.sendButtons(waId(lead),
                "Hi" + name + " 👋\nWelcome to " + props.storeName() + ".",
                List.of(new WhatsAppProvider.Button(BTN_EXPLORE_LENS, "Explore Lens 🔍")));
        logOutbound(lead.getId(), "interactive", "welcome");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
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
        StringBuilder sb = new StringBuilder("Here's what I'd pick for you 👓\n");
        for (int i = 0; i < picks.size(); i++) {
            Product p = picks.get(i);
            sb.append("\n").append(i + 1).append(") *").append(p.getName()).append("* — ")
                    .append(OrderNotificationService.money(p.getPriceMinor(), p.getCurrency()));
        }
        sb.append("\n\nReply *BUY 1*, *BUY 2* or *BUY 3* and I'll add it to your bag with a checkout link"
                + " — pay online or cash on delivery.");
        sendText(lead, sb.toString());
    }

    /** A star rating in reply to the post-purchase ask — applied to every product in that order. */
    private void handleReview(Lead lead, int rating) {
        int recorded = reviewCapture.recordAndClear(lead.getId(), rating);
        String stars = "⭐".repeat(rating);
        String thanks = recorded > 0
                ? "Thanks for the " + stars + " rating! It helps other shoppers pick the right frame."
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
        String slug = String.valueOf(slugs.get(index - 1));
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
        } else {
            StringBuilder sb = new StringBuilder("Frames matched to your face 👓\n");
            for (Product p : picks) {
                sb.append("\n• *").append(p.getName()).append("* — ")
                        .append(OrderNotificationService.money(p.getPriceMinor(), p.getCurrency()))
                        .append("\n  ").append(props.frontendBaseUrl()).append("/store/").append(p.getSlug());
            }
            sb.append("\n\nBrowse the full set & check out here:\n").append(link);
            sendText(lead, sb.toString());
        }
        analytics.record(LeadEventType.PRODUCTS_SHOWN, lead.getId(), null);
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
