package com.specskart.attribution;

import com.specskart.lead.AcquisitionSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.specskart.attribution.ProviderSupport.*;

@Component
@Order(40)
public class WhatsAppLeadSourceProvider implements LeadSourceProvider {

    @Override
    public boolean supports(Map<String, Object> raw) {
        String s = str(raw, "utm_source");
        return "whatsapp".equalsIgnoreCase(s) || raw.containsKey("wa_link") || raw.containsKey("qr");
    }

    @Override
    public AttributionContext extract(Map<String, Object> raw) {
        AttributionContext ctx = new AttributionContext();
        ctx.source = AcquisitionSource.WHATSAPP;
        ctx.externalCampaignId = str(raw, "utm_campaign");
        ctx.utm = utm(raw);
        ctx.providerMetadata.put("platform", "whatsapp");
        if (raw.get("qr") != null) ctx.providerMetadata.put("qr", raw.get("qr"));
        return ctx;
    }
}
