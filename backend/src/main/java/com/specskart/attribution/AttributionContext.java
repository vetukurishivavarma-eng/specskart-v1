package com.specskart.attribution;

import com.specskart.campaign.UtmData;
import com.specskart.lead.AcquisitionSource;

import java.util.HashMap;
import java.util.Map;

/** Normalized attribution extracted by a LeadSourceProvider from raw referral data. */
public class AttributionContext {
    public AcquisitionSource source = AcquisitionSource.UNKNOWN;
    public String externalCampaignId;
    public String adId;
    public String adSetId;
    public String clickId;
    public String referrer;
    public String landingPage;
    public UtmData utm = UtmData.empty();
    public Map<String, Object> providerMetadata = new HashMap<>();
}
