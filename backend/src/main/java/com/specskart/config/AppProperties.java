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
                           String followUpTemplate, String followUpTemplateLang, String postPurchaseTemplate,
                           /** Approved template for customer order-status updates. Blank = plain text (24h-window only). */
                           String orderUpdateTemplate,
                           /** Approved template for the staff new-order alert. Blank = plain text. */
                           String staffOrderTemplate,
                           /** WhatsApp numbers that get a plain alert when a new order is paid. Comma-separated env var; blank = off. */
                           List<String> staffNumbers,
                           /** Approved template for the automated nurture sequence. Image header +
                            *  {{1}}=first name, {{2}}=headline, {{3}}=offer line, {{4}}=shop link.
                            *  Blank = the sequence sends plain image+text (24h-window only). */
                           String nurtureTemplate,
                           /** Master switch for the automated nurture sequence. */
                           Boolean nurtureEnabled,
                           /** Absolute base URL that serves product images publicly (this API's own
                            *  origin, e.g. https://specskart-api.onrender.com). Blank = nurture messages
                            *  go text-only. */
                           String assetBaseUrl) {

        public List<String> staffNumbers() {
            return staffNumbers == null ? List.of() : staffNumbers;
        }

        /** True when the automated nurture sequence should run at all. */
        public boolean nurtureOn() {
            return nurtureEnabled == null || nurtureEnabled;
        }

        /** Approved template configured for the nurture sequence (delivers outside the 24h window). */
        public boolean nurtureConfigured() {
            return nurtureTemplate != null && !nurtureTemplate.isBlank();
        }

        /** Turn a relative product-image path into an absolute URL Meta can fetch, or null. */
        public String absoluteAsset(String path) {
            if (path == null || path.isBlank()) return null;
            if (path.startsWith("http")) return path;
            if (assetBaseUrl == null || assetBaseUrl.isBlank()) return null;
            return assetBaseUrl.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);
        }

        /** Approved template configured for customer order-status updates (delivers outside the 24h window). */
        public boolean orderUpdateConfigured() {
            return orderUpdateTemplate != null && !orderUpdateTemplate.isBlank();
        }

        /** Approved template configured for the staff new-order alert. */
        public boolean staffOrderConfigured() {
            return staffOrderTemplate != null && !staffOrderTemplate.isBlank();
        }

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
