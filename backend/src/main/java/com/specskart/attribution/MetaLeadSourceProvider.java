package com.specskart.attribution;

import com.specskart.lead.AcquisitionSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.specskart.attribution.ProviderSupport.*;

@Component
@Order(10)
public class MetaLeadSourceProvider implements LeadSourceProvider {

    @Override
    public boolean supports(Map<String, Object> raw) {
        String src = str(raw, "utm_source");
        return raw.containsKey("ctwa_clid") || raw.containsKey("referral")
                || (src != null && (src.contains("facebook") || src.contains("instagram")
                    || src.contains("meta") || src.contains("fb") || src.contains("ig")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public AttributionContext extract(Map<String, Object> raw) {
        AttributionContext ctx = new AttributionContext();
        ctx.source = AcquisitionSource.META;
        ctx.clickId = str(raw, "ctwa_clid");
        Object referral = raw.get("referral");
        if (referral instanceof Map<?, ?> r) {
            Map<String, Object> rm = (Map<String, Object>) r;
            ctx.externalCampaignId = str(rm, "source_id");
            ctx.adId = str(rm, "source_id");
            ctx.referrer = str(rm, "source_url");
            ctx.providerMetadata.put("referral", r);
        }
        ctx.externalCampaignId = firstNonNull(str(raw, "campaign_id"), ctx.externalCampaignId);
        ctx.adId = firstNonNull(str(raw, "ad_id"), ctx.adId);
        ctx.adSetId = str(raw, "adset_id");
        ctx.landingPage = str(raw, "landing_page");
        ctx.utm = utm(raw);
        ctx.providerMetadata.put("platform", "meta");
        return ctx;
    }
}
