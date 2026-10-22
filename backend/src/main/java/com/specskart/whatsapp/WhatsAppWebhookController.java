package com.specskart.whatsapp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final AppProperties props;
    private final WhatsAppInboundService inbound;
    private final ObjectMapper mapper;
    private final StaffAlerts staffAlerts;
    private final SentMessageRepository sent;
    private final WhatsAppMessageRepository chatLog;

    public WhatsAppWebhookController(AppProperties props, WhatsAppInboundService inbound, ObjectMapper mapper,
                                     StaffAlerts staffAlerts, SentMessageRepository sent, WhatsAppMessageRepository chatLog) {
        this.chatLog = chatLog;
        this.staffAlerts = staffAlerts;
        this.sent = sent;
        this.props = props;
        this.inbound = inbound;
        this.mapper = mapper;
    }

    /** Meta webhook verification handshake. */
    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
                                         @RequestParam("hub.verify_token") String token,
                                         @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && props.whatsapp().webhookVerifyToken() != null
                && props.whatsapp().webhookVerifyToken().equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        String secret = props.whatsapp().appSecret();
        if (secret != null && !secret.isBlank()) {
            if (!validSignature(rawBody, signature, secret)) {
                log.warn("webhook signature rejected");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    JsonNode contacts = value.path("contacts");
                    JsonNode messages = value.path("messages");
                    for (JsonNode msg : messages) {
                        inbound.process(toInbound(msg, contacts));
                        String quoted = msg.path("context").path("id").asText("");
                        if (!quoted.isBlank()) { // they swiped-to-reply on one of our messages
                            chatLog.findFirstByWaMessageId(msg.path("id").asText("")).ifPresent(m -> {
                                m.setReplyToWaId(quoted);
                                chatLog.save(m);
                            });
                        }
                    }
                    // delivery / read statuses are logged only -- but a failure loudly: Meta accepts a
                    // send and only reports here that it never arrived (e.g. 131047, outside the 24h
                    // window), which is how a staff alert used to vanish without a trace.
                    for (JsonNode status : value.path("statuses")) {
                        JsonNode err = status.path("errors").path(0);
                        try { // a staff alert's receipt closes it, or re-queues it on failure
                            staffAlerts.onDeliveryStatus(status.path("id").asText(null), status.path("status").asText(""),
                                    err.path("code").asText() + " " + err.path("title").asText());
                        } catch (Exception e) {
                            log.warn("staff alert status update failed: {}", e.getMessage());
                        }
                        tick(status.path("id").asText(""), status.path("status").asText(""));
                        if ("failed".equals(status.path("status").asText())) {
                            String to = status.path("recipient_id").asText("");
                            log.warn("wa send FAILED to {}•••• msg={} code={} {}",
                                    to.length() > 4 ? to.substring(0, to.length() - 4) : to,
                                    status.path("id").asText(), err.path("code").asText(),
                                    err.path("title").asText() + " " + err.path("error_data").path("details").asText(""));
                        } else {
                            log.debug("wa status {} for {}", status.path("status").asText(), status.path("id").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("webhook processing error", e);
            // 200 anyway so Meta does not spam retries for a poison payload; we logged it.
        }
        return ResponseEntity.ok().build();
    }

    private static final java.util.List<String> TICKS = java.util.List.of("sent", "delivered", "read");

    /** The ticks on a message in the staff chat: only ever forward (a late "delivered" can't undo "read"). */
    private void tick(String wamid, String status) {
        if (wamid.isBlank()) return;
        chatLog.findFirstByWaMessageId(wamid).ifPresent(m -> {
            boolean forward = "failed".equals(status)
                    || TICKS.indexOf(status) > TICKS.indexOf(m.getStatus() == null ? "" : m.getStatus());
            if (forward && !"failed".equals(m.getStatus())) {
                m.setStatus(status);
                chatLog.save(m);
            }
        });
    }

    private InboundMessage toInbound(JsonNode msg, JsonNode contacts) {
        String waId = msg.path("from").asText(null);
        String profileName = null;
        String phone = waId;
        if (contacts.isArray() && !contacts.isEmpty()) {
            JsonNode c = contacts.get(0);
            profileName = c.path("profile").path("name").asText(null);
            phone = c.path("wa_id").asText(waId);
        }
        String type = msg.path("type").asText("");
        String text = null;
        String buttonId = null;
        switch (type) {
            case "text" -> text = msg.path("text").path("body").asText(null);
            case "button" -> { text = msg.path("button").path("text").asText(null);
                               buttonId = msg.path("button").path("payload").asText(null); }
            case "interactive" -> {
                JsonNode i = msg.path("interactive");
                if (i.has("button_reply")) {
                    buttonId = i.path("button_reply").path("id").asText(null);
                    text = i.path("button_reply").path("title").asText(null);
                } else if (i.has("list_reply")) {
                    buttonId = i.path("list_reply").path("id").asText(null);
                    text = i.path("list_reply").path("title").asText(null);
                } else { // a WhatsApp Flow form (nfm_reply) or a newer kind we don't build
                    log.info("wa interactive reply from {}: {}", waId, msg);
                    text = WhatsAppBotService.STAFF_ONLY + "Sent a form reply the app can't read. Ask them what they need.";
                }
            }
            // Meta hides the content of some messages from the Cloud API (edited, poll, view-once, some
            // ad replies...) and sends only an error. Say so plainly, and keep the raw JSON so we can see why.
            case "unsupported" -> {
                log.warn("wa unsupported message from {}: {}", waId, msg);
                JsonNode err = msg.path("errors").path(0);
                String kind = msg.path("unsupported").path("type").asText(""); // e.g. "edit", "poll"
                String why = !kind.isBlank() ? kind
                        : err.path("error_data").path("details").asText(err.path("title").asText(""));
                String ad = msg.path("referral").path("headline").asText(msg.path("referral").path("body").asText(""));
                text = "(Customer sent a message WhatsApp doesn't show us"
                        + (why.isBlank() ? "" : ": " + why) + (ad.isBlank() ? "" : " | from ad: " + ad)
                        + ". Check the WhatsApp app or ask them to type it again.)";
            }
            // an emoji on one of our messages: shown in the chat, never answered (see WhatsAppBotService)
            case "reaction" -> {
                String emoji = msg.path("reaction").path("emoji").asText("");
                String on = sent.findById(msg.path("reaction").path("message_id").asText(""))
                        .map(SentMessage::getBody).filter(b -> b != null && !b.isBlank())
                        .map(b -> " to: \"" + (b.length() > 80 ? b.substring(0, 80).trim() + "…" : b).replace('\n', ' ') + "\"")
                        .orElse(""); // a message from before we kept these
                text = WhatsAppBotService.REACTION + (emoji.isBlank() ? "(took back their reaction)" : emoji) + on;
            }
            // WhatsApp's own notice, not something the customer typed: "X changed their phone number to Y",
            // "security code changed"... Shown in the chat, never answered by the bot.
            case "system" -> {
                log.info("wa system message from {}: {}", waId, msg);
                String body = msg.path("system").path("body").asText("");
                text = WhatsAppBotService.SYSTEM + (body.isBlank() ? msg.path("system").path("type").asText("notice") : body);
            }
            case "location" -> {
                JsonNode l = msg.path("location");
                String place = String.join(", ", java.util.stream.Stream.of(l.path("name").asText(""), l.path("address").asText(""))
                        .filter(x -> !x.isBlank()).toList());
                text = WhatsAppBotService.STAFF_ONLY + "📍 Shared a location" + (place.isBlank() ? "" : ": " + place)
                        + "\nhttps://maps.google.com/?q=" + l.path("latitude").asText() + "," + l.path("longitude").asText();
            }
            case "contacts" -> {
                StringBuilder b = new StringBuilder(WhatsAppBotService.STAFF_ONLY + "👤 Shared a contact");
                for (JsonNode c : msg.path("contacts")) {
                    b.append("\n").append(c.path("name").path("formatted_name").asText("(no name)"));
                    for (JsonNode ph : c.path("phones")) b.append(" · ").append(ph.path("phone").asText(""));
                }
                text = b.toString();
            }
            case "order" -> text = WhatsAppBotService.STAFF_ONLY + "🛒 Sent an order from the WhatsApp catalog ("
                    + msg.path("order").path("product_items").size() + " item(s)). Ask them which frames they want.";
            // they opened the chat for the first time: the bot answers it like a hello
            case "request_welcome" -> text = "(Opened the chat)";
            // media: shown in the chat by its id (picked up below)
            case "image", "video", "audio", "document", "sticker" -> text = "[" + type + "]";
            default -> { // anything WhatsApp adds later: never a blank "[xyz]" with a menu fired back
                log.warn("wa unhandled message type {} from {}: {}", type, waId, msg);
                text = WhatsAppBotService.STAFF_ONLY + "Sent a kind of message the app can't show (" + type
                        + "). Ask them to type it.";
            }
        }
        Map<String, Object> referral = null;
        if (msg.has("referral")) {
            referral = mapper.convertValue(msg.path("referral"), LinkedHashMap.class);
            // Normalize a couple of fields the attribution providers look for.
            referral.put("ctwa_clid", msg.path("referral").path("ctwa_clid").asText(null));
        }
        return new InboundMessage(waId, phone, profileName, text, buttonId,
                msg.path("id").asText(null), referral, msg.path(type).path("id").asText(null),
                msg.path(type).path("filename").asText(null));
    }

    private boolean validSignature(byte[] body, String header, String secret) {
        if (header == null || !header.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            return constantTimeEquals(expected, header);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
