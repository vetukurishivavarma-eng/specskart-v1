package com.specskart.whatsapp;

import java.util.List;

/** Transport abstraction over the WhatsApp Business Platform. */
public interface WhatsAppProvider {

    String mode();

    void sendText(String toWaId, String text);

    void sendButtons(String toWaId, String bodyText, List<Button> buttons);

    /**
     * An interactive list — up to 10 tappable rows, each with an optional description.
     * Buttons cap at 3 and carry no description, so anything longer than a yes/no belongs
     * here. A tapped row comes back through the webhook as {@code interactive.list_reply},
     * which the controller already maps onto the same buttonId as a button reply.
     */
    void sendList(String toWaId, String bodyText, String buttonLabel, List<Row> rows);

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

    /**
     * {@link #sendTemplate} for a template with a "Copy offer code" button (Meta's COPY_CODE,
     * always button index 0 -- a template may carry only one). Meta rejects the send if the
     * template has that button and no code arrives for it, or the other way round.
     */
    default void sendCouponTemplate(String toWaId, String templateName, String languageCode,
                                    List<String> bodyParams, String couponCode) {
        sendTemplate(toWaId, templateName, languageCode, bodyParams);
    }

    /** A document (PDF) by public link with an optional caption (≤1024 chars). Inside the 24h window only. */
    void sendDocument(String toWaId, String documentUrl, String filename, String caption);

    /** An approved template with a DOCUMENT header — how a PDF reaches a number outside the 24h window. */
    void sendDocumentTemplate(String toWaId, String templateName, String languageCode,
                              String documentUrl, String filename, List<String> bodyParams);

    record Button(String id, String title) {}

    /** One row of a list message. {@code description} may be null. */
    record Row(String id, String title, String description) {}
}
