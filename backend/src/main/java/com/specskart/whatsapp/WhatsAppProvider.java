package com.specskart.whatsapp;

import java.util.List;

/** Transport abstraction over the WhatsApp Business Platform. */
public interface WhatsAppProvider {

    String mode();

    void sendText(String toWaId, String text);

    void sendButtons(String toWaId, String bodyText, List<Button> buttons);

    record Button(String id, String title) {}
}
