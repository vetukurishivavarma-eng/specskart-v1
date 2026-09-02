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

    public record Sent(String toWaId, String text, List<Button> buttons) {}

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

    public synchronized List<Sent> outbox() {
        return List.copyOf(outbox);
    }
}
