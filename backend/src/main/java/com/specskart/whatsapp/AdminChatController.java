package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.shared.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminChatController.class);

    /** How long one staff message keeps the bot quiet. Matches Meta's service window. */
    private static final long QUIET_HOURS = 24;

    private final LeadRepository leads;
    private final WhatsAppMessageRepository messages;
    private final WhatsAppProvider provider;
    private final ServiceWindow window;
    private final WhatsAppBotService bot;
    private final AppProperties props;
    private final LeadNudges nudges;
    private final com.specskart.lead.LeadPrescriptionService prescriptions;
    private final SentMessageRepository sent;

    public AdminChatController(LeadRepository leads, WhatsAppMessageRepository messages, WhatsAppProvider provider,
                               ServiceWindow window, WhatsAppBotService bot, AppProperties props, LeadNudges nudges,
                               com.specskart.lead.LeadPrescriptionService prescriptions, SentMessageRepository sent) {
        this.sent = sent;
        this.prescriptions = prescriptions;
        this.nudges = nudges;
        this.leads = leads;
        this.messages = messages;
        this.provider = provider;
        this.window = window;
        this.bot = bot;
        this.props = props;
    }

    /** replyToId: one of this chat's messages (our id) to quote, like swipe-to-reply. */
    public record SendRequest(String body, java.util.UUID replyToId) {
        public SendRequest(String body) {
            this(body, null);
        }
    }

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
        List<WhatsAppMessage> log = lead == null ? List.of() : messages.findByLeadIdOrderByCreatedAtAsc(lead.getId());
        Map<String, WhatsAppMessage> byWamid = new java.util.HashMap<>();
        for (WhatsAppMessage m : log) if (m.getWaMessageId() != null) byWamid.put(m.getWaMessageId(), m);
        out.put("messages", log.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("at", m.getCreatedAt());
            row.put("direction", m.getDirection());
            row.put("body", m.getBody() == null ? "" : m.getBody());
            row.put("type", m.getMessageType() == null ? "" : m.getMessageType());
            row.put("mediaId", m.getMediaId() == null ? "" : m.getMediaId());
            row.put("status", m.getStatus() == null ? "" : m.getStatus());
            row.put("canReply", m.getWaMessageId() != null); // reply / react need WhatsApp's id
            if (m.getReplyToWaId() != null) row.put("replyTo", quote(byWamid.get(m.getReplyToWaId()), m.getReplyToWaId()));
            return row;
        }).toList());
        return out;
    }

    @PostMapping("/messages")
    @Transactional
    public Map<String, Object> send(@PathVariable String waId, @RequestBody SendRequest req) {
        String text = req.body() == null ? "" : req.body().trim();
        if (text.isEmpty()) throw ApiException.badRequest("EMPTY_MESSAGE", "Type a message first.");
        if (text.length() > 4000) throw ApiException.badRequest("MESSAGE_TOO_LONG", "Keep it under 4000 characters.");
        Lead lead = lead(waId);
        requireOpen(lead);
        String replyTo = replyWamid(lead, req.replyToId());
        String wamid;
        try {
            wamid = provider.sendText(lead.getWhatsappWaId(), text, replyTo);
        } catch (Exception e) {
            throw ApiException.badRequest("SEND_FAILED", "WhatsApp didn't accept the message. Try again in a minute.");
        }
        staffSent(lead, "agent", text, wamid, null, replyTo);
        return get(waId);
    }

    /** A photo or file from the staff phone (camera, gallery or files), with an optional caption. */
    @PostMapping(value = "/attachments", consumes = "multipart/form-data")
    @Transactional
    public Map<String, Object> attach(@PathVariable String waId,
                                      @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                      @RequestParam(value = "caption", required = false) String caption,
                                      @RequestParam(value = "replyToId", required = false) java.util.UUID replyToId)
            throws java.io.IOException {
        Lead lead = lead(waId);
        requireOpen(lead);
        if (file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "Pick a photo or file first.");
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String type = MetaWhatsAppProvider.mediaType(mime);
        long max = type.equals("image") ? 5_000_000 : 16_000_000; // WhatsApp's own limits
        if (file.getSize() > max) {
            throw ApiException.badRequest("TOO_BIG", "That file is too big for WhatsApp (" + max / 1_000_000 + " MB max).");
        }
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "file." + extension(mime) : file.getOriginalFilename();
        String replyTo = replyWamid(lead, replyToId);
        WhatsAppProvider.SentMedia out;
        try {
            out = provider.sendUpload(lead.getWhatsappWaId(), file.getBytes(), mime, name,
                    caption == null ? null : caption.trim(), replyTo);
        } catch (Exception e) {
            log.warn("chat attachment to {} failed: {}", waId, e.toString());
            throw ApiException.badRequest("SEND_FAILED", "WhatsApp didn't accept the file. Try a smaller one, or a photo/PDF.");
        }
        String body = caption != null && !caption.isBlank() ? caption.trim()
                : type.equals("document") ? name : "[" + type + "]";
        staffSent(lead, type, body, out.messageId(), out.mediaId(), replyTo);
        return get(waId);
    }

    public record ReactRequest(String emoji) {}

    /** An emoji on one message, like a long-press in WhatsApp ("" takes it back). */
    @PostMapping("/messages/{id}/react")
    @Transactional
    public Map<String, Object> react(@PathVariable String waId, @PathVariable java.util.UUID id, @RequestBody ReactRequest req) {
        Lead lead = lead(waId);
        requireOpen(lead);
        String emoji = req.emoji() == null ? "" : req.emoji().trim();
        if (emoji.length() > 16) throw ApiException.badRequest("BAD_EMOJI", "Pick one emoji.");
        WhatsAppMessage target = messages.findById(id)
                .filter(m -> m.getLeadId().equals(lead.getId()) && m.getWaMessageId() != null)
                .orElseThrow(() -> ApiException.notFound("NO_MESSAGE", "That message can't be reacted to."));
        try {
            provider.sendReaction(lead.getWhatsappWaId(), target.getWaMessageId(), emoji);
        } catch (Exception e) {
            throw ApiException.badRequest("SEND_FAILED", "WhatsApp didn't accept the reaction. Try again in a minute.");
        }
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(lead.getId());
        m.setDirection("OUTBOUND");
        m.setMessageType("reaction");
        m.setBody((emoji.isEmpty() ? "Took back the reaction" : "Reacted " + emoji) + " to: \"" + quote(target, null) + "\"");
        m.setStatus("sent");
        messages.save(m);
        return get(waId);
    }

    /** Staff opened the chat: the customer's messages show blue ticks, as when you read them on WhatsApp. */
    @PostMapping("/read")
    public Map<String, Object> read(@PathVariable String waId) {
        Lead lead = lead(waId);
        messages.findFirstByLeadIdAndDirectionAndWaMessageIdIsNotNullOrderByCreatedAtDesc(lead.getId(), "INBOUND")
                .ifPresent(m -> provider.markRead(m.getWaMessageId()));
        return Map.of("ok", true);
    }

    private void requireOpen(Lead lead) {
        if (!window.isOpen(lead.getWhatsappWaId())) {
            throw ApiException.conflict("WINDOW_CLOSED",
                    "This customer hasn't messaged in the last 24 hours, so WhatsApp only allows an approved "
                            + "template. Send the follow-up template; once they reply you can chat freely.");
        }
    }

    /** WhatsApp's id for the message being replied to; one without an id is just sent unquoted. */
    private String replyWamid(Lead lead, java.util.UUID replyToId) {
        if (replyToId == null) return null;
        return messages.findById(replyToId).filter(m -> m.getLeadId().equals(lead.getId()))
                .map(WhatsAppMessage::getWaMessageId).orElse(null);
    }

    /** Logs what staff sent and keeps the bot quiet while they chat. */
    private void staffSent(Lead lead, String type, String body, String wamid, String mediaId, String replyTo) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(lead.getId());
        m.setDirection("OUTBOUND");
        m.setMessageType(type);
        m.setWaMessageId(wamid);
        m.setMediaId(mediaId);
        m.setReplyToWaId(replyTo);
        m.setBody(body);
        m.setStatus("sent");
        messages.save(m);

        lead.setAgentChatUntil(Instant.now().plus(QUIET_HOURS, ChronoUnit.HOURS));
        lead.setLastContactAt(Instant.now());
        leads.save(lead);
        nudges.staffReplied(lead.getId()); // stop the "still waiting" pushes; schedule the one follow-up
    }

    /** The quoted line above a reply: the words of the message it answers. */
    private String quote(WhatsAppMessage m, String wamid) {
        String body = m != null ? m.getBody()
                : wamid == null ? null : sent.findById(wamid).map(SentMessage::getBody).orElse(null); // a bot message we only indexed
        if (body == null || body.isBlank()) return "a message";
        body = body.replace('\n', ' ');
        return body.length() > 100 ? body.substring(0, 100).trim() + "…" : body;
    }

    /**
     * A photo/file the customer sent, streamed from Meta (their URL needs our token, so the app
     * can't load it directly). Only media that arrived in THIS chat -- not any id a caller guesses.
     */
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<byte[]> media(@PathVariable String waId, @PathVariable String mediaId) {
        if (!messages.existsByLeadIdAndMediaId(lead(waId).getId(), mediaId)) {
            throw ApiException.notFound("NO_MEDIA", "That photo isn't in this chat.");
        }
        WhatsAppProvider.Media media;
        try {
            media = provider.downloadMedia(mediaId);
        } catch (Exception e) {
            log.warn("chat media {} for {} failed: {}", mediaId, waId, e.toString());
            // Meta keeps inbound media for a limited time; older photos are simply gone
            throw ApiException.notFound("MEDIA_GONE", "WhatsApp no longer has this photo.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                // a real extension so the phone knows which app opens it (PDF viewer, video player...)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaId + "." + extension(media.mimeType()) + "\"")
                .body(media.bytes());
    }

    /** Keep this chat photo on the customer as a prescription (our own copy: WhatsApp drops media after ~30 days). */
    @PostMapping("/media/{mediaId}/prescription")
    public Map<String, Object> saveAsPrescription(@PathVariable String waId, @PathVariable String mediaId,
                                                  org.springframework.security.core.Authentication auth) {
        Lead lead = lead(waId);
        var p = prescriptions.savePhoto(lead, mediaId, auth != null ? String.valueOf(auth.getPrincipal()) : "system");
        return Map.of("id", p.getId(), "leadId", lead.getId());
    }

    /** Outside the 24h window: the approved re-engagement template, the only thing Meta allows. */
    @PostMapping("/template")
    public Map<String, Object> template(@PathVariable String waId) {
        bot.sendManualFollowUp(lead(waId));
        return get(waId);
    }

    public record StartRequest(String name) {}

    /**
     * Staff message a number that has never messaged us: here {waId} is whatever number they
     * typed. Saves them as a lead and sends the approved follow-up template -- the only first
     * message Meta delivers to a new contact. Checked before saving, so no template = no junk lead.
     */
    @PostMapping("/start")
    @Transactional
    public Map<String, Object> start(@PathVariable String waId, @RequestBody(required = false) StartRequest req) {
        // they messaged us in the last 24h: the chat is open, so staff just type (free) -- no paid template
        String open = com.specskart.shared.PhoneNumbers.normalize(waId, props.whatsapp().defaultCountryCode());
        if (open != null && window.isOpen(open)) return get(open);
        if (!props.whatsapp().followUpConfigured()) {
            throw ApiException.conflict("FOLLOW_UP_TEMPLATE_NOT_CONFIGURED",
                    "Messaging a new number needs an approved WhatsApp template, which isn't set up yet.");
        }
        String digits = com.specskart.shared.PhoneNumbers.normalize(waId, props.whatsapp().defaultCountryCode());
        if (digits == null || digits.length() < 10 || digits.length() > 15) {
            throw ApiException.badRequest("BAD_NUMBER", "That doesn't look like a phone number. Include the full number, e.g. 0971234567.");
        }
        String name = req == null || req.name() == null || req.name().isBlank() ? null : req.name().trim();
        Lead lead = leads.findByWhatsappWaId(digits).orElseGet(() -> {
            Lead l = new Lead();
            l.setWhatsappWaId(digits);
            l.setWhatsappNumber(digits);
            l.setFirstContactAt(Instant.now());
            return l;
        });
        if (name != null && (lead.getName() == null || lead.getName().isBlank())) lead.setName(name);
        lead.setLastContactAt(Instant.now());
        leads.save(lead);
        try {
            bot.sendManualFollowUp(lead);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("SEND_FAILED", "WhatsApp didn't accept the message. Check the number and try again.");
        }
        return get(digits);
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

    /** What WhatsApp lets customers send; anything else downloads as .bin. */
    public static String extension(String mime) {
        String m = mime == null ? "" : mime.split(";")[0].trim().toLowerCase();
        return switch (m) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            case "video/3gpp" -> "3gp";
            case "audio/ogg" -> "ogg";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4" -> "m4a";
            case "audio/aac" -> "aac";
            case "audio/amr" -> "amr";
            case "text/plain" -> "txt";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            default -> "bin";
        };
    }

    private Lead lead(String waId) {
        return leads.findByWhatsappWaId(digits(waId)).orElseThrow(() -> ApiException.notFound("NO_CHAT",
                "This customer has never messaged the business number, so there's no WhatsApp chat to continue."));
    }

    private static String digits(String waId) {
        return waId == null ? "" : waId.replaceAll("[^0-9]", "");
    }
}
