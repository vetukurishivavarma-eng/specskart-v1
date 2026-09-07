package com.specskart.whatsapp;

import java.util.List;

/** Transport abstraction over the WhatsApp Business Platform. */
public interface WhatsAppProvider {

    String mode();

    void sendText(String toWaId, String text);

    void sendButtons(String toWaId, String bodyText, List<Button> buttons);

    /**
     * Send an approved message template. This is the ONLY message type Meta accepts
     * outside the 24-hour customer-service window, so agent-initiated re-engagement of
     * a cold lead must go through here. {@code bodyParams} fill the template's {{1}},
     * {{2}}, … placeholders in order.
     */
    void sendTemplate(String toWaId, String templateName, String languageCode, List<String> bodyParams);

    record Button(String id, String title) {}
}
