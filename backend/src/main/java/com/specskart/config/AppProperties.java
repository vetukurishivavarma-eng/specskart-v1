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
        Payments payments,
        Promo promo,
        Loyalty loyalty,
        Lenses lenses
) {
    /** Per-pair price add-on (minor units) for each prescription-lens option. Non-prescription = 0. */
    public record Lenses(long singleVisionAddMinor, long progressiveAddMinor, long blueLightAddMinor) {
        public long addFor(String lensType) {
            if (lensType == null) return 0;
            return switch (lensType.toUpperCase(java.util.Locale.ROOT)) {
                case "SINGLE_VISION" -> singleVisionAddMinor;
                case "PROGRESSIVE" -> progressiveAddMinor;
                case "BLUE_LIGHT" -> blueLightAddMinor;
                default -> 0;
            };
        }
    }
    public record Session(int expiryHours) {}
    public record Promo(int faceAnalysisPercent, int faceAnalysisHours) {}

    /**
     * pointsPerKwacha: points earned per K1 of order subtotal.
     * pointValueMinor: what one point is worth as a checkout discount (in ngwee).
     * referralFriendPercent: discount a new customer gets when using a referral code.
     * referralRewardPoints: points the referrer earns once the friend's order is paid.
     * minRedeemPoints: smallest redemption allowed.
     */
    public record Loyalty(double pointsPerKwacha, int pointValueMinor, int referralFriendPercent,
                          int referralRewardPoints, int minRedeemPoints) {}
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
