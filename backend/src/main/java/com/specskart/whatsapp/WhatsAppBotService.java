package com.specskart.whatsapp;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.config.AppProperties;
import com.specskart.framefinder.FrameFinderService;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import com.specskart.lead.LeadStatus;
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
    static final String BTN_EXPERT = "TALK_TO_EXPERT";
    static final String BTN_WEBSITE = "VISIT_WEBSITE";
    static final String BTN_RESULTS_FRAMES = "RESULTS_SHOW_FRAMES";
    static final String BTN_RESULTS_NOT_NOW = "RESULTS_NOT_NOW";

    private final WhatsAppProvider provider;
    private final WhatsAppMessageRepository messages;
    private final FrameFinderService frameFinder;
    private final LeadService leadService;
    private final AnalyticsService analytics;
    private final AppProperties props;

    public WhatsAppBotService(WhatsAppProvider provider, WhatsAppMessageRepository messages,
                              FrameFinderService frameFinder, LeadService leadService,
                              AnalyticsService analytics, AppProperties props) {
        this.provider = provider;
        this.messages = messages;
        this.frameFinder = frameFinder;
        this.leadService = leadService;
        this.analytics = analytics;
        this.props = props;
    }

    @Transactional
    public void handleInbound(Lead lead, String text, String buttonId, String waMessageId) {
        logInbound(lead.getId(), waMessageId, text, buttonId);
        BotIntent intent = classify(text, buttonId);
        log.info("bot intent {} for lead {}", intent, lead.getId());
        switch (intent) {
            case FIND_FRAMES -> sendFrameFinderLink(lead);
            case EXPLORE_FRAMES -> sendText(lead, "Our latest collection is here: "
                    + props.frontendBaseUrl() + "/store  (online ordering is launching soon — reply here to reserve).");
            case TALK_TO_EXPERT -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.INTERESTED);
                analytics.record(LeadEventType.EXPERT_CONTACT_REQUESTED, lead.getId(), null);
                sendText(lead, "An eyewear expert from " + props.storeName()
                        + " will message you shortly. Meanwhile, feel free to share what you're looking for.");
            }
            case VISIT_WEBSITE -> sendText(lead, "Here's our website: " + props.frontendBaseUrl());
            case RESULTS_SHOW_FRAMES -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.INTERESTED);
                sendText(lead, "Great! Browse styles matched to your face here: "
                        + props.frontendBaseUrl() + "/store  — or reply and our expert will curate a set for you.");
            }
            case RESULTS_NOT_NOW -> {
                leadService.advanceStatusSoft(lead.getId(), LeadStatus.FOLLOW_UP);
                sendText(lead, "No problem — your recommendations are saved. Message us anytime to pick up where you left off.");
            }
            case GREETING, UNKNOWN -> sendWelcome(lead);
        }
    }

    public void sendWelcome(Lead lead) {
        String name = lead.getName() != null ? " " + lead.getName().split(" ")[0] : "";
        provider.sendButtons(waId(lead),
                "Hi" + name + " 👋\nWelcome to " + props.storeName()
                        + ".\n\nI can help you find frames that complement your face. What would you like to do?",
                List.of(new WhatsAppProvider.Button(BTN_FIND, "Find Frames For My Face"),
                        new WhatsAppProvider.Button(BTN_EXPLORE, "Explore Frames"),
                        new WhatsAppProvider.Button(BTN_EXPERT, "Talk To An Expert")));
        logOutbound(lead.getId(), "interactive", "welcome");
        analytics.record(LeadEventType.WHATSAPP_AUTOREPLY_SENT, lead.getId(), null);
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

    /** Called after face analysis completes. */
    @Transactional
    public void sendAnalysisFollowUp(Lead lead, String faceShapeDisplay, List<String> recommended) {
        String body = "Your frame analysis is ready 🎯\n\nFace match: " + faceShapeDisplay
                + "\n\nFrames we recommend:\n"
                + recommended.stream().map(r -> "• " + r).reduce((a, b) -> a + "\n" + b).orElse("")
                + "\n\nWould you like our eyewear expert to show you matching frames?";
        provider.sendButtons(waId(lead), body,
                List.of(new WhatsAppProvider.Button(BTN_RESULTS_FRAMES, "Show Me Frames"),
                        new WhatsAppProvider.Button(BTN_EXPERT, "Talk To Expert"),
                        new WhatsAppProvider.Button(BTN_RESULTS_NOT_NOW, "Not Now")));
        logOutbound(lead.getId(), "interactive", "analysis-follow-up");
        analytics.record(LeadEventType.WHATSAPP_RESULTS_REQUESTED, lead.getId(), null);
    }

    BotIntent classify(String text, String buttonId) {
        if (buttonId != null) {
            try {
                return switch (buttonId) {
                    case BTN_FIND -> BotIntent.FIND_FRAMES;
                    case BTN_EXPLORE -> BotIntent.EXPLORE_FRAMES;
                    case BTN_EXPERT -> BotIntent.TALK_TO_EXPERT;
                    case BTN_WEBSITE -> BotIntent.VISIT_WEBSITE;
                    case BTN_RESULTS_FRAMES -> BotIntent.RESULTS_SHOW_FRAMES;
                    case BTN_RESULTS_NOT_NOW -> BotIntent.RESULTS_NOT_NOW;
                    default -> BotIntent.UNKNOWN;
                };
            } catch (Exception ignored) { }
        }
        String t = text == null ? "" : text.toLowerCase().trim();
        if (t.isBlank()) return BotIntent.GREETING;
        if (t.matches(".*(hi|hello|hey|start|namaste).*") && t.length() < 15) return BotIntent.GREETING;
        if (t.contains("face") || t.contains("suit") || t.contains("frame finder") || t.equals("1")) return BotIntent.FIND_FRAMES;
        if (t.contains("explore") || t.contains("latest") || t.contains("catalog") || t.equals("2")) return BotIntent.EXPLORE_FRAMES;
        if (t.contains("expert") || t.contains("talk") || t.contains("call") || t.equals("3")) return BotIntent.TALK_TO_EXPERT;
        if (t.contains("website") || t.contains("site") || t.equals("4")) return BotIntent.VISIT_WEBSITE;
        return BotIntent.UNKNOWN;
    }

    private String waId(Lead lead) {
        return lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
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
