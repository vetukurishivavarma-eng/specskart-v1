package com.specskart.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Default provider. Records outbound messages to an in-memory log instead of calling Meta. */
@Component
@ConditionalOnProperty(name = "specskart.whatsapp.provider", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppProvider.class);

    public record Sent(String toWaId, String text, List<Button> buttons,
                       String templateName, List<String> templateParams, String imageUrl) {
        public Sent(String toWaId, String text, List<Button> buttons) {
            this(toWaId, text, buttons, null, List.of(), null);
        }
        public Sent(String toWaId, String text, List<Button> buttons, String templateName, List<String> templateParams) {
            this(toWaId, text, buttons, templateName, templateParams, null);
        }
    }

    private final List<Sent> outbox = new ArrayList<>();

    @Override
    public String mode() {
        return "MOCK";
    }

    @Override
    public synchronized void sendText(String toWaId, String text) {
        outbox.add(new Sent(toWaId, text, List.of()));
        log.info("[MOCK-WA] -> {} : {}", toWaId, text);
    }

    @Override
    public synchronized void sendButtons(String toWaId, String bodyText, List<Button> buttons) {
        outbox.add(new Sent(toWaId, bodyText, buttons));
        log.info("[MOCK-WA] -> {} : {} buttons={}", toWaId, bodyText,
                buttons.stream().map(Button::title).toList());
    }

    @Override
    public synchronized void sendTemplate(String toWaId, String templateName, String languageCode, List<String> bodyParams) {
        outbox.add(new Sent(toWaId, null, List.of(), templateName, List.copyOf(bodyParams)));
        log.info("[MOCK-WA] -> {} : template={} lang={} params={}", toWaId, templateName, languageCode, bodyParams);
    }

    @Override
    public synchronized void sendImage(String toWaId, String imageUrl, String caption) {
        outbox.add(new Sent(toWaId, caption, List.of(), null, List.of(), imageUrl));
        log.info("[MOCK-WA] -> {} : image={} caption={}", toWaId, imageUrl, caption);
    }

    @Override
    public synchronized void sendMediaTemplate(String toWaId, String templateName, String languageCode,
                                               String headerImageUrl, List<String> bodyParams) {
        outbox.add(new Sent(toWaId, null, List.of(), templateName, List.copyOf(bodyParams), headerImageUrl));
        log.info("[MOCK-WA] -> {} : mediaTemplate={} lang={} header={} params={}",
                toWaId, templateName, languageCode, headerImageUrl, bodyParams);
    }

    public synchronized List<Sent> outbox() {
        return List.copyOf(outbox);
    }
}
