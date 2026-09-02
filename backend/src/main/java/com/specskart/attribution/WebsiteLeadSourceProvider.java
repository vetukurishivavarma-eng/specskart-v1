package com.specskart.attribution;

import com.specskart.lead.AcquisitionSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.specskart.attribution.ProviderSupport.*;

/** Fallback provider — always matches, lowest precedence. */
@Component
@Order(1000)
public class WebsiteLeadSourceProvider implements LeadSourceProvider {

    @Override
    public boolean supports(Map<String, Object> raw) {
        return true;
    }

    @Override
    public AttributionContext extract(Map<String, Object> raw) {
        AttributionContext ctx = new AttributionContext();
        ctx.source = AcquisitionSource.WEBSITE;
        ctx.referrer = str(raw, "referrer");
        ctx.landingPage = str(raw, "landing_page");
        ctx.utm = utm(raw);
        ctx.providerMetadata.put("platform", "website");
        return ctx;
    }
}
