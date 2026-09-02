package com.specskart.attribution;

import com.specskart.lead.AcquisitionSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.specskart.attribution.ProviderSupport.*;

@Component
@Order(20)
public class GoogleLeadSourceProvider implements LeadSourceProvider {

    @Override
    public boolean supports(Map<String, Object> raw) {
        String s = str(raw, "utm_source");
        return raw.containsKey("gclid") || (s != null && s.contains("google"));
    }

    @Override
    public AttributionContext extract(Map<String, Object> raw) {
        AttributionContext ctx = new AttributionContext();
        ctx.source = AcquisitionSource.GOOGLE;
        ctx.clickId = str(raw, "gclid");
        ctx.externalCampaignId = str(raw, "utm_campaign");
        ctx.landingPage = str(raw, "landing_page");
        ctx.utm = utm(raw);
        ctx.providerMetadata.put("platform", "google");
        return ctx;
    }
}
