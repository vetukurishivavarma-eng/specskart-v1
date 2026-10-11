package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.shared.ApiException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff chatting with a customer FROM THE BUSINESS NUMBER, inside the POS app.
 *
 * The business number lives on Meta's Cloud API, so it cannot be opened in anyone's WhatsApp app
 * -- a wa.me link always opens the staff member's own (personal) WhatsApp. Messages go out through
 * the same provider the bot uses instead.
 *
 * Meta's rule: a plain message only within 24h of the customer's last message to us. Outside that,
 * only an approved template -- the existing re-engagement one -- can reopen the conversation.
 * While staff are chatting the bot stays quiet ({@link Lead#getAgentChatUntil()}).
 *
 * Keyed by the customer's WhatsApp number so the till's Orders screen (AGENT) and the Leads screen
 * (ADMIN) share it. AGENT + ADMIN via SecurityConfig's /api/admin/** rule.
 */
@RestController
@RequestMapping("/api/admin/chat/{waId}")
public class AdminChatController {

    /** How long one staff message keeps the bot quiet. Matches Meta's service window. */
    private static final long QUIET_HOURS = 24;

    private final LeadRepository leads;
    private final WhatsAppMessageRepository messages;
    private final WhatsAppProvider provider;
    private final ServiceWindow window;
    private final WhatsAppBotService bot;
    private final AppProperties props;

    public AdminChatController(LeadRepository leads, WhatsAppMessageRepository messages, WhatsAppProvider provider,
                               ServiceWindow window, WhatsAppBotService bot, AppProperties props) {
        this.leads = leads;
        this.messages = messages;
        this.provider = provider;
        this.window = window;
        this.bot = bot;
        this.props = props;
    }

    public record SendRequest(String body) {}

    @GetMapping
    public Map<String, Object> get(@PathVariable String waId) {
        String digits = digits(waId);
        Lead lead = leads.findByWhatsappWaId(digits).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("waId", digits);
        out.put("name", lead == null ? null : lead.getName());
        out.put("known", lead != null);
        out.put("windowOpen", window.isOpen(digits));
        out.put("templateAvailable", lead != null && props.whatsapp().followUpConfigured());
        Instant quiet = lead == null ? null : lead.getAgentChatUntil();
        out.put("botQuietUntil", quiet != null && quiet.isAfter(Instant.now()) ? quiet : null);
        out.put("messages", lead == null ? List.of() : messages.findByLeadIdOrderByCreatedAtAsc(lead.getId()).stream()
                .map(m -> Map.of(
                        "at", m.getCreatedAt(),
                        "direction", m.getDirection(),
                        "body", m.getBody() == null ? "" : m.getBody(),
                        "type", m.getMessageType() == null ? "" : m.getMessageType()))
                .toList());
        return out;
    }

    @PostMapping("/messages")
    @Transactional
    public Map<String, Object> send(@PathVariable String waId, @RequestBody SendRequest req) {
        String text = req.body() == null ? "" : req.body().trim();
        if (text.isEmpty()) throw ApiException.badRequest("EMPTY_MESSAGE", "Type a message first.");
        if (text.length() > 4000) throw ApiException.badRequest("MESSAGE_TOO_LONG", "Keep it under 4000 characters.");
        Lead lead = lead(waId);
        if (!window.isOpen(lead.getWhatsappWaId())) {
            throw ApiException.conflict("WINDOW_CLOSED",
                    "This customer hasn't messaged in the last 24 hours, so WhatsApp only allows an approved "
                            + "template. Send the follow-up template; once they reply you can chat freely.");
        }

        String wamid;
        try {
            wamid = provider.sendText(lead.getWhatsappWaId(), text);
        } catch (Exception e) {
            throw ApiException.badRequest("SEND_FAILED", "WhatsApp didn't accept the message. Try again in a minute.");
        }

        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(lead.getId());
        m.setDirection("OUTBOUND");
        m.setMessageType("agent");
        m.setWaMessageId(wamid);
        m.setBody(text);
        m.setStatus("sent");
        messages.save(m);

        lead.setAgentChatUntil(Instant.now().plus(QUIET_HOURS, ChronoUnit.HOURS));
        lead.setLastContactAt(Instant.now());
        leads.save(lead);
        return get(waId);
    }

    /** Outside the 24h window: the approved re-engagement template, the only thing Meta allows. */
    @PostMapping("/template")
    public Map<String, Object> template(@PathVariable String waId) {
        bot.sendManualFollowUp(lead(waId));
        return get(waId);
    }

    /** Hand the conversation back to the bot before the quiet period runs out. */
    @PostMapping("/bot")
    @Transactional
    public Map<String, Object> resumeBot(@PathVariable String waId) {
        Lead lead = lead(waId);
        lead.setAgentChatUntil(null);
        leads.save(lead);
        return get(waId);
    }

    private Lead lead(String waId) {
        return leads.findByWhatsappWaId(digits(waId)).orElseThrow(() -> ApiException.notFound("NO_CHAT",
                "This customer has never messaged the business number, so there's no WhatsApp chat to continue."));
    }

    private static String digits(String waId) {
        return waId == null ? "" : waId.replaceAll("[^0-9]", "");
    }
}
