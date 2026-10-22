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
                       String templateName, List<String> templateParams, String imageUrl, String documentUrl) {
        public Sent(String toWaId, String text, List<Button> buttons) {
            this(toWaId, text, buttons, null, List.of(), null, null);
        }
        public Sent(String toWaId, String text, List<Button> buttons, String templateName, List<String> templateParams) {
            this(toWaId, text, buttons, templateName, templateParams, null, null);
        }
        public Sent(String toWaId, String text, List<Button> buttons, String templateName,
                    List<String> templateParams, String imageUrl) {
            this(toWaId, text, buttons, templateName, templateParams, imageUrl, null);
        }
    }

    private final List<Sent> outbox = new ArrayList<>();
    private final SentMessageRepository sent;

    public MockWhatsAppProvider(SentMessageRepository sent) {
        this.sent = sent;
    }

    @Override
    public String mode() {
        return "MOCK";
    }

    @Override
    public synchronized String sendText(String toWaId, String text) {
        failIfAsked();
        outbox.add(new Sent(toWaId, text, List.of()));
        log.info("[MOCK-WA] -> {} : {}", toWaId, text);
        SentMessage m = new SentMessage(); // as the real provider does, so reactions can be tested
        m.setWaMessageId("wamid.mock-" + java.util.UUID.randomUUID());
        m.setToWaId(toWaId);
        m.setBody(text);
        sent.save(m);
        return m.getWaMessageId();
    }

    @Override
    public synchronized void sendButtons(String toWaId, String bodyText, List<Button> buttons) {
        outbox.add(new Sent(toWaId, bodyText, buttons));
        log.info("[MOCK-WA] -> {} : {} buttons={}", toWaId, bodyText,
                buttons.stream().map(Button::title).toList());
    }

    /** Rows land in the outbox as {@code buttons} — same id + title a test wants to assert on. */
    @Override
    public synchronized void sendList(String toWaId, String bodyText, String buttonLabel, List<Row> rows) {
        outbox.add(new Sent(toWaId, bodyText, rows.stream().map(r -> new Button(r.id(), r.title())).toList()));
        log.info("[MOCK-WA] -> {} : {} list[{}]={}", toWaId, bodyText, buttonLabel,
                rows.stream().map(Row::title).toList());
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

    @Override
    public synchronized String sendDocument(String toWaId, String documentUrl, String filename, String caption) {
        failIfAsked();
        outbox.add(new Sent(toWaId, caption, List.of(), null, List.of(), null, documentUrl));
        log.info("[MOCK-WA] -> {} : document={} ({}) caption={}", toWaId, documentUrl, filename, caption);
        return "wamid.mock-" + outbox.size();
    }

    @Override
    public synchronized String sendDocumentTemplate(String toWaId, String templateName, String languageCode,
                                                  String documentUrl, String filename, List<String> bodyParams) {
        failIfAsked();
        outbox.add(new Sent(toWaId, null, List.of(), templateName, List.copyOf(bodyParams), null, documentUrl));
        log.info("[MOCK-WA] -> {} : documentTemplate={} doc={} params={}", toWaId, templateName, documentUrl, bodyParams);
        return "wamid.mock-" + outbox.size();
    }

    /** Every media id is a 1x1 PNG locally, so chat photos and "save as prescription" work without Meta. */
    @Override
    public Media downloadMedia(String mediaId) {
        return new Media(java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="), "image/png");
    }

    @Override
    public synchronized String sendText(String toWaId, String text, String replyToWamid) {
        return sendText(toWaId, text);
    }

    @Override
    public synchronized String sendReaction(String toWaId, String wamid, String emoji) {
        outbox.add(new Sent(toWaId, "reaction " + emoji + " on " + wamid, List.of()));
        return "wamid.mock-" + java.util.UUID.randomUUID();
    }

    @Override
    public synchronized SentMedia sendUpload(String toWaId, byte[] bytes, String mimeType, String filename,
                                             String caption, String replyToWamid) {
        failIfAsked();
        outbox.add(new Sent(toWaId, caption, List.of(), null, List.of(), null, filename));
        return new SentMedia("wamid.mock-" + java.util.UUID.randomUUID(), "media-mock-" + java.util.UUID.randomUUID());
    }

    public synchronized List<Sent> outbox() {
        return List.copyOf(outbox);
    }

    private int failNext;

    /** Tests: the next {@code n} text/document sends throw, like Meta rejecting them. */
    public synchronized void failNext(int n) {
        failNext = n;
    }

    private void failIfAsked() {
        if (failNext > 0) {
            failNext--;
            throw new IllegalStateException("mock send failure");
        }
    }
}
