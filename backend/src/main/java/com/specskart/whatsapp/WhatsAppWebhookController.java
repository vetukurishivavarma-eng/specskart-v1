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

    public WhatsAppWebhookController(AppProperties props, WhatsAppInboundService inbound, ObjectMapper mapper) {
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
                    }
                    // delivery / read statuses are logged only
                    for (JsonNode status : value.path("statuses")) {
                        log.debug("wa status {} for {}", status.path("status").asText(), status.path("id").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.error("webhook processing error", e);
            // 200 anyway so Meta does not spam retries for a poison payload; we logged it.
        }
        return ResponseEntity.ok().build();
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
                }
            }
            default -> text = "[" + type + "]";
        }
        Map<String, Object> referral = null;
        if (msg.has("referral")) {
            referral = mapper.convertValue(msg.path("referral"), LinkedHashMap.class);
            // Normalize a couple of fields the attribution providers look for.
            referral.put("ctwa_clid", msg.path("referral").path("ctwa_clid").asText(null));
        }
        return new InboundMessage(waId, phone, profileName, text, buttonId,
                msg.path("id").asText(null), referral);
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
