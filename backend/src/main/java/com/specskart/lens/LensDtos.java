package com.specskart.lens;

import java.math.BigDecimal;
import java.util.UUID;

public final class LensDtos {
    private LensDtos() {}

    /** @param attribution raw query params off the page they landed on (fbclid / ttclid /
     *                     gclid / utm_*), so a lead can be traced back to the ad that paid for it. */
    public record StartVerification(String phone, String lensType, Boolean blueBlock,
                                    java.util.Map<String, Object> attribution) {}
    public record StartResult(UUID inquiryId) {}
    public record VerifyResult(boolean verified, UUID inquiryId) {}

    public record UpdateDetails(String customerName, Integer age, String gender,
                                BigDecimal sphRight, BigDecimal sphLeft,
                                BigDecimal cylRight, BigDecimal cylLeft,
                                Integer axisRight, Integer axisLeft,
                                BigDecimal addPower, String lensStructure) {}

    /** Full current state of an inquiry — used to gate the form, resume after a refresh,
     *  and drive the poll-until-verified step. */
    public record InquiryView(UUID id, String status, boolean verified,
                              String lensType, boolean blueBlock,
                              String customerName, Integer age, String gender,
                              BigDecimal sphRight, BigDecimal sphLeft,
                              BigDecimal cylRight, BigDecimal cylLeft,
                              Integer axisRight, Integer axisLeft,
                              BigDecimal addPower, String lensStructure,
                              boolean specialAxis, Long priceMinor, String currency,
                              String fulfilment, boolean paid) {}

    /** Hosted-checkout hop for a lens order: where to send the shopper, and what they'll pay. */
    public record PayResult(String checkoutUrl, long amountMinor, String currency) {}

    /** Specskart POS: staff billing a customer at the counter, no WhatsApp step. */
    public record WalkInSale(String customerName, String phone, String lensType, Boolean blueBlock,
                             BigDecimal addPower, String lensStructure,
                             String paymentMethod, String soldBy, String shopName, String clientReference,
                             UUID storeId) {}

    public record CompleteSale(String paymentMethod, String soldBy, String shopName) {}

    /** Staff moving a web order one step along the collection ladder. Payment details are only
     *  needed on the last step, where the order is also billed. */
    public record AdvanceFulfilment(String stage, String paymentMethod, String soldBy, String shopName) {}

    /** One row in the POS's sales list / day summary. */
    public record SaleView(UUID id, String customerName, String lensType, boolean blueBlock,
                           String lensStructure, boolean specialAxis, long priceMinor, String currency,
                           String paymentMethod, String soldBy, String shopName, boolean walkIn,
                           String fulfilment, boolean paid, java.time.Instant createdAt) {}

    public record DaySummary(long totalMinor, int count, String currency) {}
}
