package com.specskart.whatsapp;

import java.util.Map;

/** Normalized inbound WhatsApp message, provider-agnostic. */
public record InboundMessage(
        String waId,
        String phoneNumber,
        String profileName,
        String text,
        String buttonId,
        String waMessageId,
        Map<String, Object> referral,
        String mediaId,  // inbound photo/file, else null
        String mediaName // a document's filename, else null
) {
    public InboundMessage(String waId, String phoneNumber, String profileName, String text, String buttonId,
                          String waMessageId, Map<String, Object> referral) {
        this(waId, phoneNumber, profileName, text, buttonId, waMessageId, referral, null, null);
    }
}
