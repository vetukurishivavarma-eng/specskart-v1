package com.specskart.attribution;

import com.specskart.lead.AcquisitionSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.specskart.attribution.ProviderSupport.*;

@Component
@Order(30)
public class TikTokLeadSourceProvider implements LeadSourceProvider {

    @Override
    public boolean supports(Map<String, Object> raw) {
        String s = str(raw, "utm_source");
        return raw.containsKey("ttclid") || (s != null && s.contains("tiktok"));
    }

    @Override
    public AttributionContext extract(Map<String, Object> raw) {
        AttributionContext ctx = new AttributionContext();
        ctx.source = AcquisitionSource.TIKTOK;
        ctx.clickId = str(raw, "ttclid");
        ctx.externalCampaignId = str(raw, "utm_campaign");
        ctx.landingPage = str(raw, "landing_page");
        ctx.utm = utm(raw);
        ctx.providerMetadata.put("platform", "tiktok");
        return ctx;
    }
}
