package com.specskart.campaign;

import jakarta.persistence.Embeddable;

@Embeddable
public record UtmData(
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String utmContent,
        String utmTerm
) {
    public static UtmData empty() { return new UtmData(null, null, null, null, null); }
}
