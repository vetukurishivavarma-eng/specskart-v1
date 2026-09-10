package com.specskart.whatsapp;

import java.util.List;

/** Transport abstraction over the WhatsApp Business Platform. */
public interface WhatsAppProvider {

    String mode();

    void sendText(String toWaId, String text);

    void sendButtons(String toWaId, String bodyText, List<Button> buttons);

    /** An image message with an optional caption. Inside the 24h window only (like sendText). */
    void sendImage(String toWaId, String imageUrl, String caption);

    /**
     * An approved template with an image header. The only way to send a picture to a
     * cold lead outside the 24h window (marketing / utility category as approved in
     * Meta). {@code headerImageUrl} may be null for a text-only template.
     */
    void sendMediaTemplate(String toWaId, String templateName, String languageCode,
                           String headerImageUrl, List<String> bodyParams);

    /**
     * Send an approved message template. This is the ONLY message type Meta accepts
     * outside the 24-hour customer-service window, so agent-initiated re-engagement of
     * a cold lead must go through here. {@code bodyParams} fill the template's {{1}},
     * {{2}}, … placeholders in order.
     */
    void sendTemplate(String toWaId, String templateName, String languageCode, List<String> bodyParams);

    record Button(String id, String title) {}
}
