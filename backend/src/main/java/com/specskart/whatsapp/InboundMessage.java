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
        Map<String, Object> referral
) {}
