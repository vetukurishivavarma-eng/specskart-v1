package com.specskart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "specskart")
public record AppProperties(
        String storeName,
        String frontendBaseUrl,
        String businessWhatsappNumber,
        List<String> corsOrigins,
        Session session,
        Face face,
        WhatsApp whatsapp,
        Payments payments
) {
    public record Session(int expiryHours) {}
    public record Payments(String provider, Flutterwave flutterwave) {}
    public record Flutterwave(String secretKey, String secretHash, String baseUrl) {}
    public record Face(boolean retainImages) {}
    public record WhatsApp(String provider, String phoneNumberId, String businessAccountId,
                           String accessToken, String webhookVerifyToken, String appSecret, String graphBaseUrl,
                           String followUpTemplate, String followUpTemplateLang, String postPurchaseTemplate) {

        /** True when a re-engagement template is configured for agent-initiated follow-ups. */
        public boolean followUpConfigured() {
            return followUpTemplate != null && !followUpTemplate.isBlank();
        }

        /** Approved template for the post-delivery "thanks + come back" message (params: name, promo code). */
        public boolean postPurchaseConfigured() {
            return postPurchaseTemplate != null && !postPurchaseTemplate.isBlank();
        }
    }
}
