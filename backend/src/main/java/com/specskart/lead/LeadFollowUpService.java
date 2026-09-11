package com.specskart.lead;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.catalog.CatalogService;
import com.specskart.catalog.Product;
import com.specskart.catalog.ProductImage;
import com.specskart.catalog.ProductImageRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoIssuer;
import com.specskart.config.AppProperties;
import com.specskart.order.CartService;
import com.specskart.order.OrderNotificationService;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Automated WhatsApp nurture: once a lead has been analysed but not bought, walk
 * them through a few timed product touches — their matches, what's trending, the
 * lens pitch, a last call — until they convert or opt out. All state lives on the
 * {@link Lead}; {@link LeadFollowUpJob} drives it on a schedule.
 */
@Service
public class LeadFollowUpService {

    private static final Logger log = LoggerFactory.getLogger(LeadFollowUpService.class);

    enum Kind { MATCHES, TRENDING, LENS_VALUE, LAST_CALL, GOODBYE }

    record Step(Duration delayAfterPrevious, String key, Kind kind) {}

    /** The sequence. Delay is measured from the previous touch (or from enrolment for step 0). */
    static final List<Step> STEPS = List.of(
            new Step(Duration.ofDays(2), "matches", Kind.MATCHES),
            new Step(Duration.ofDays(3), "trending", Kind.TRENDING),
            new Step(Duration.ofDays(4), "lens_value", Kind.LENS_VALUE),
            new Step(Duration.ofDays(4), "last_call", Kind.LAST_CALL),
            new Step(Duration.ofDays(5), "goodbye", Kind.GOODBYE));

    /** Number of touches in the sequence. */
    public static int stepCount() { return STEPS.size(); }

    /** Statuses a lead can be auto-enrolled from (engaged with the funnel, no purchase). */
    static final List<LeadStatus> ENROLLABLE_STATUSES = List.of(
            LeadStatus.ENGAGED, LeadStatus.FACE_ANALYSIS_COMPLETED,
            LeadStatus.INTERESTED, LeadStatus.FOLLOW_UP);

    private final LeadRepository leads;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final CatalogService catalog;
    private final ProductImageRepository productImages;
    private final PromoIssuer promoIssuer;
    private final CartService carts;
    private final AnalyticsService analytics;
    private final AppProperties props;

    public LeadFollowUpService(LeadRepository leads, WhatsAppProvider whatsapp,
                               WhatsAppMessageRepository messages, CatalogService catalog,
                               ProductImageRepository productImages, PromoIssuer promoIssuer,
                               CartService carts, AnalyticsService analytics, AppProperties props) {
        this.leads = leads;
        this.whatsapp = whatsapp;
        this.messages = messages;
        this.catalog = catalog;
        this.productImages = productImages;
        this.promoIssuer = promoIssuer;
        this.carts = carts;
        this.analytics = analytics;
        this.props = props;
    }

    // ---- lifecycle -------------------------------------------------------------

    /** Put a cold lead into the sequence. Idempotent — only acts when not yet enrolled. */
    @Transactional
    public void enroll(UUID leadId) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.getFollowUpState() != null) return;
        if (!props.whatsapp().nurtureOn()) return;
        if (lead.getStatus() == LeadStatus.CONVERTED || lead.getStatus() == LeadStatus.LOST) return;
        if (waId(lead) == null) return;
        lead.setFollowUpState(FollowUpState.ACTIVE);
        lead.setFollowUpStep(0);
        lead.setFollowUpNextAt(Instant.now().plus(STEPS.get(0).delayAfterPrevious()));
        leads.save(lead);
        log.info("enrolled lead {} in the WhatsApp nurture sequence", leadId);
    }

    /** Any inbound message from the lead. STOP words opt them out; anything else defers the next touch. */
    @Transactional
    public void onInbound(UUID leadId, String text) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.getFollowUpState() != FollowUpState.ACTIVE) return;
        String t = text == null ? "" : text.toLowerCase().trim();
        if (t.matches(".*\\b(stop|unsubscribe|opt ?out|opt-out|no more|don'?t message)\\b.*")) {
            end(lead, FollowUpState.OPTED_OUT);
            safeSend(() -> whatsapp.sendText(waId(lead),
                    "Done — no more follow-ups. Message us any time you'd like a hand picking frames."));
            return;
        }
        // they're engaging — hold off on the automated touches for a few days
        lead.setFollowUpNextAt(Instant.now().plus(Duration.ofDays(3)));
        leads.save(lead);
    }

    /** The lead bought something — stop with a success outcome. */
    @Transactional
    public void onConverted(UUID leadId) {
        if (leadId == null) return;
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead != null && lead.getFollowUpState() == FollowUpState.ACTIVE) end(lead, FollowUpState.CONVERTED);
    }

    /** Job hook: send the one touch that's due for this lead, then schedule the next. */
    @Transactional
    public void runDueTouch(UUID leadId) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.getFollowUpState() != FollowUpState.ACTIVE) return;
        if (lead.getFollowUpNextAt() == null || lead.getFollowUpNextAt().isAfter(Instant.now())) return;
        if (lead.getStatus() == LeadStatus.CONVERTED) { end(lead, FollowUpState.CONVERTED); return; }
        if (lead.getStatus() == LeadStatus.LOST || waId(lead) == null) { end(lead, FollowUpState.DONE); return; }

        int step = lead.getFollowUpStep();
        if (step >= STEPS.size()) { end(lead, FollowUpState.DONE); return; }
        Step s = STEPS.get(step);
        try {
            sendTouch(lead, s);
        } catch (Exception e) {
            log.warn("nurture touch {} failed for lead {}: {} — retrying later", s.key(), leadId, e.getMessage());
            lead.setFollowUpNextAt(Instant.now().plus(6, ChronoUnit.HOURS));
            leads.save(lead);
            return;
        }
        analytics.record(LeadEventType.WHATSAPP_NURTURE_SENT, leadId, null);
        int next = step + 1;
        lead.setFollowUpStep(next);
        if (next >= STEPS.size()) {
            end(lead, FollowUpState.DONE);
        } else {
            lead.setFollowUpNextAt(Instant.now().plus(STEPS.get(next).delayAfterPrevious()));
            leads.save(lead);
        }
    }

    private void end(Lead lead, FollowUpState terminal) {
        lead.setFollowUpState(terminal);
        lead.setFollowUpNextAt(null);
        leads.save(lead);
        analytics.record(LeadEventType.WHATSAPP_NURTURE_ENDED, lead.getId(), null);
        log.info("nurture sequence for lead {} ended: {}", lead.getId(), terminal);
    }

    // ---- admin ---------------------------------------------------------------

    /** Admin: (re)start the sequence for a lead from the top, whatever state it's in. */
    @Transactional
    public void adminRestart(UUID leadId) {
        Lead lead = leads.findById(leadId).orElseThrow();
        lead.setFollowUpState(FollowUpState.ACTIVE);
        lead.setFollowUpStep(0);
        lead.setFollowUpNextAt(Instant.now().plus(STEPS.get(0).delayAfterPrevious()));
        leads.save(lead);
    }

    /** Admin: stop the sequence now (no more touches). */
    @Transactional
    public void adminStop(UUID leadId) {
        Lead lead = leads.findById(leadId).orElseThrow();
        lead.setFollowUpState(FollowUpState.DONE);
        lead.setFollowUpNextAt(null);
        leads.save(lead);
    }

    public java.util.Map<String, Object> statusOf(Lead lead) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("state", lead.getFollowUpState() == null ? "NOT_ENROLLED" : lead.getFollowUpState().name());
        m.put("step", lead.getFollowUpStep());
        m.put("totalSteps", STEPS.size());
        m.put("nextAt", lead.getFollowUpNextAt());
        return m;
    }

    // ---- message building -----------------------------------------------------

    private void sendTouch(Lead lead, Step s) {
        String first = firstName(lead);
        String shopLink = shopLink(lead);
        boolean windowOpen = windowOpen(lead.getId());
        boolean useTemplate = !windowOpen && props.whatsapp().nurtureConfigured();

        if (s.kind() == Kind.GOODBYE) {
            String msg = "We'll pause the frame suggestions here, " + first + " 👋\n"
                    + "Whenever you're ready we're one message away — or browse any time:\n" + shopLink;
            if (useTemplate) {
                whatsapp.sendMediaTemplate(waId(lead), props.whatsapp().nurtureTemplate(),
                        props.whatsapp().followUpTemplateLang(), null,
                        List.of(first, "We'll pause here for now", "Browse whenever you're ready", shopLink));
            } else {
                whatsapp.sendText(waId(lead), msg);
            }
            logOutbound(lead.getId(), useTemplate ? "template" : "text", "nurture:" + s.key());
            return;
        }

        // Dynamic offer: a HOT lead is already close to buying — showing the picks alone is
        // usually enough, so don't spend margin on a discount at the first touch. A COLD one
        // who's ignored the whole sequence gets one bigger, shorter-lived push at the end.
        LeadScoring.Score score = LeadScoring.of(lead);
        boolean skipDiscount = s.kind() == Kind.MATCHES && score.temperature() == LeadScoring.Temperature.HOT;
        PromoCode promo = skipDiscount ? null
                : s.kind() == Kind.LAST_CALL ? promoIssuer.forLastCall(lead.getId())
                : promoIssuer.forFaceAnalysis(lead.getId());
        String offer = promo == null ? "See your picks below"
                : promo.getDiscountValue() + "% off your first pair with code " + promo.getCode()
                  + (promo.getExpiresAt() != null ? " (" + hoursLeft(promo) + "h left)" : "");

        List<Product> picks = switch (s.kind()) {
            case MATCHES -> lead.getFaceShape() == null ? catalog.trending(3) : catalog.forFaceShape(lead.getFaceShape(), 3);
            case TRENDING -> catalog.trending(3);
            case LENS_VALUE, LAST_CALL -> {
                List<Product> byFace = lead.getFaceShape() == null ? List.of() : catalog.forFaceShape(lead.getFaceShape(), 1);
                yield byFace.isEmpty() ? catalog.trending(1) : byFace;
            }
            case GOODBYE -> List.of();
        };

        String headline = switch (s.kind()) {
            case MATCHES -> lead.getFaceShape() == null ? "Frames picked for you"
                    : "Frames matched to your " + lead.getFaceShape().toLowerCase() + " face";
            case TRENDING -> "Trending at " + props.storeName() + " this week";
            case LENS_VALUE -> "Your frames, fitted with lenses";
            case LAST_CALL -> "Your discount is about to expire";
            default -> "";
        };

        String heroImage = picks.isEmpty() ? null : firstImageUrl(picks.get(0).getId());

        if (useTemplate) {
            whatsapp.sendMediaTemplate(waId(lead), props.whatsapp().nurtureTemplate(),
                    props.whatsapp().followUpTemplateLang(), heroImage,
                    List.of(first, headline, offer, shopLink));
            logOutbound(lead.getId(), "template", "nurture:" + s.key());
            return;
        }

        StringBuilder body = new StringBuilder(headline).append(" 👓\n");
        for (Product p : picks) {
            body.append("\n• *").append(p.getName()).append("* — ")
                    .append(OrderNotificationService.money(p.getPriceMinor(), p.getCurrency()))
                    .append("\n  ").append(props.frontendBaseUrl()).append("/store/").append(p.getSlug());
        }
        if (s.kind() == Kind.LENS_VALUE) {
            body.append("\n\nEvery pair comes fitted with your prescription lenses — anti-glare and "
                    + "scratch-resistant — and delivered across Zambia.");
        }
        body.append("\n\n🎁 ").append(offer);
        body.append("\n\nShop & check out here:\n").append(shopLink);

        if (heroImage != null) {
            whatsapp.sendImage(waId(lead), heroImage, body.toString());
        } else {
            whatsapp.sendText(waId(lead), body.toString());
        }
        logOutbound(lead.getId(), heroImage != null ? "image" : "text", "nurture:" + s.key());
    }

    // ---- helpers ------------------------------------------------------------

    private boolean windowOpen(UUID leadId) {
        return messages.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(leadId, "INBOUND")
                .map(m -> m.getCreatedAt().isAfter(Instant.now().minus(24, ChronoUnit.HOURS)))
                .orElse(false);
    }

    private String firstImageUrl(UUID productId) {
        return productImages.findByProductIdOrderBySortAsc(productId).stream()
                .map(ProductImage::getUrl)
                .map(props.whatsapp()::absoluteAsset)
                .filter(u -> u != null)
                .findFirst().orElse(null);
    }

    private String shopLink(Lead lead) {
        String token = carts.startForLead(lead.getId()).getToken();
        String url = props.frontendBaseUrl() + "/store?c=" + token;
        if (lead.getFaceShape() != null) url += "&face=" + lead.getFaceShape();
        return url;
    }

    private String waId(Lead lead) {
        return lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
    }

    private static String firstName(Lead lead) {
        return lead.getName() != null && !lead.getName().isBlank()
                ? lead.getName().trim().split("\\s+")[0] : "there";
    }

    private static long hoursLeft(PromoCode promo) {
        return Math.max(1, Duration.between(Instant.now(), promo.getExpiresAt()).toHours());
    }

    private void safeSend(Runnable send) {
        try { send.run(); } catch (Exception e) { log.warn("nurture opt-out ack failed: {}", e.getMessage()); }
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
