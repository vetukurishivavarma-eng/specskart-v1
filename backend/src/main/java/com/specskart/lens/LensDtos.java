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

    /** Full current state of an inquiry — used to gate the form, resume after a refresh,
     *  and drive the poll-until-verified step. */
    public record InquiryView(UUID id, String status, boolean verified,
                              String lensType, boolean blueBlock,
                              String customerName, Integer age, String gender,
                              BigDecimal sphRight, BigDecimal sphLeft,
                              BigDecimal cylRight, BigDecimal cylLeft,
                              Integer axisRight, Integer axisLeft,
                              BigDecimal addPower, String lensStructure,
                              boolean specialAxis, Long priceMinor, String currency) {}
}
