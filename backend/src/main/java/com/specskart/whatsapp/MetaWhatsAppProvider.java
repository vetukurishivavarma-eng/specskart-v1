package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Real WhatsApp Business Cloud API transport (Graph API /{phone-number-id}/messages). */
@Component
@ConditionalOnProperty(name = "specskart.whatsapp.provider", havingValue = "meta")
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppProvider.class);

    private final RestClient http;
    private final AppProperties props;

    public MetaWhatsAppProvider(RestClient http, AppProperties props) {
        this.http = http;
        this.props = props;
        if (props.whatsapp().accessToken() == null || props.whatsapp().phoneNumberId() == null) {
            throw new IllegalStateException("WhatsApp provider=meta requires WHATSAPP_ACCESS_TOKEN and WHATSAPP_PHONE_NUMBER_ID");
        }
    }

    @Override
    public String mode() {
        return "META";
    }

    private String url() {
        return props.whatsapp().graphBaseUrl() + "/" + props.whatsapp().phoneNumberId() + "/messages";
    }

    @Override
    public void sendText(String toWaId, String text) {
        post(Map.of(
                "messaging_product", "whatsapp",
                "to", toWaId,
                "type", "text",
                "text", Map.of("preview_url", false, "body", text)));
    }

    @Override
    public void sendButtons(String toWaId, String bodyText, List<Button> buttons) {
        var rows = buttons.stream()
                .map(b -> Map.of("type", "reply", "reply", Map.of("id", b.id(), "title", cap(b.title()))))
                .toList();
        post(Map.of(
                "messaging_product", "whatsapp",
                "to", toWaId,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", bodyText),
                        "action", Map.of("buttons", rows))));
    }

    private static String cap(String s) {
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    private void post(Map<String, Object> payload) {
        try {
            http.post().uri(url())
                    .header("Authorization", "Bearer " + props.whatsapp().accessToken())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("WhatsApp send failed: {}", e.getMessage());
            throw e;
        }
    }
}
