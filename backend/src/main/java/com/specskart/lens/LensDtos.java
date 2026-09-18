package com.specskart.lens;

import java.math.BigDecimal;
import java.util.UUID;

public final class LensDtos {
    private LensDtos() {}

    public record StartVerification(String phone, String lensType, Boolean blueBlock) {}
    public record StartResult(UUID inquiryId) {}
    public record VerifyResult(boolean verified, UUID inquiryId) {}

    public record UpdateDetails(String customerName, Integer age, String gender,
                                BigDecimal sphRight, BigDecimal sphLeft,
                                BigDecimal cylRight, BigDecimal cylLeft,
                                Integer axisRight, Integer axisLeft,
                                BigDecimal addPower, String lensStructure) {}

    /** Where the lens gets delivered — its own step after the quote, so it stays out of
     *  the (positional) UpdateDetails record the prescription form uses. */
    public record Delivery(String name, String address, String area, String landmark) {}

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
                              String deliveryName, String deliveryAddress,
                              String deliveryArea, String deliveryLandmark) {}

    /** Specskart POS: staff billing a customer at the counter, no WhatsApp step. */
    public record WalkInSale(String customerName, String phone, String lensType, Boolean blueBlock,
                             BigDecimal addPower, String lensStructure,
                             String paymentMethod, String soldBy, String shopName, String clientReference) {}

    public record CompleteSale(String paymentMethod, String soldBy, String shopName) {}

    /** One row in the POS's sales list / day summary. */
    public record SaleView(UUID id, String customerName, String lensType, boolean blueBlock,
                           String lensStructure, boolean specialAxis, long priceMinor, String currency,
                           String paymentMethod, String soldBy, String shopName, boolean walkIn,
                           String deliveryName, String deliveryAddress, String deliveryArea,
                           String deliveryLandmark, java.time.Instant createdAt) {}

    public record DaySummary(long totalMinor, int count, String currency) {}
}
