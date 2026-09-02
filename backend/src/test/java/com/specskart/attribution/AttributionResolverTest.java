package com.specskart.attribution;

import com.specskart.lead.AcquisitionSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttributionResolverTest {

    private final AttributionResolver resolver = new AttributionResolver(List.of(
            new MetaLeadSourceProvider(), new GoogleLeadSourceProvider(), new TikTokLeadSourceProvider(),
            new WhatsAppLeadSourceProvider(), new WebsiteLeadSourceProvider()));

    @Test
    void metaClickToWhatsAppReferralIsAttributedToMeta() {
        var ctx = resolver.resolve(Map.of(
                "referral", Map.of("source_id", "camp-1", "source_url", "https://fb.com/x"),
                "ctwa_clid", "abc", "utm_source", "facebook", "utm_campaign", "sep"));
        assertThat(ctx.source).isEqualTo(AcquisitionSource.META);
        assertThat(ctx.externalCampaignId).isEqualTo("camp-1");
        assertThat(ctx.clickId).isEqualTo("abc");
    }

    @Test
    void googleClickIdIsAttributedToGoogle() {
        var ctx = resolver.resolve(Map.of("gclid", "g1", "utm_source", "google", "utm_campaign", "rx"));
        assertThat(ctx.source).isEqualTo(AcquisitionSource.GOOGLE);
    }

    @Test
    void unknownTrafficFallsBackToWebsite() {
        var ctx = resolver.resolve(Map.of("referrer", "https://example.com"));
        assertThat(ctx.source).isEqualTo(AcquisitionSource.WEBSITE);
    }
}
