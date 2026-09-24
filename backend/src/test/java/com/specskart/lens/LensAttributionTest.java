package com.specskart.lens;

import com.specskart.campaign.Campaign;
import com.specskart.campaign.CampaignRepository;
import com.specskart.campaign.Platform;
import com.specskart.campaign.UtmData;
import com.specskart.lead.AcquisitionSource;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An ad click that lands on the website has to survive all the way to the lead, which is the
 * one thing the attribution providers never got: the lead is created in the /verify request,
 * a different browser entirely (WhatsApp opens the link in its own), so the landing params
 * are parked on the inquiry and replayed there.
 */
@SpringBootTest
@ActiveProfiles("mock")
class LensAttributionTest {

    @Autowired LensInquiryService service;
    @Autowired LensInquiryRepository inquiries;
    @Autowired LeadRepository leads;
    @Autowired CampaignRepository campaigns;
    @Autowired WhatsAppProvider provider;

    private String phone() {
        return "097" + (7000000 + (int) (Math.random() * 900000));
    }

    private String tokenFromLastOutbound() {
        var sent = ((MockWhatsAppProvider) provider).outbox();
        String text = sent.get(sent.size() - 1).text();
        return text.substring(text.lastIndexOf('/') + 1);
    }

    @Test
    void aFacebookClickIdOnTheLandingUrlReachesTheLeadAndMatchesItsCampaign() {
        String utmCampaign = "lens-launch-" + UUID.randomUUID().toString().substring(0, 8);
        Campaign c = new Campaign();
        c.setName("Lens launch");
        c.setPlatform(Platform.FACEBOOK);
        c.setUtm(new UtmData("facebook", "paid_social", utmCampaign, null, null));
        campaigns.save(c);

        UUID id = service.start(phone(), "CLEAR", false, Map.of(
                "fbclid", "IwAR-test-click",
                "utm_source", "facebook",
                "utm_campaign", utmCampaign,
                "landing_page", "/lens?fbclid=IwAR-test-click"));

        // Nothing is attributed yet -- there is no lead until the number verifies.
        assertThat(inquiries.findById(id).orElseThrow().getLeadId()).isNull();

        service.verify(tokenFromLastOutbound());

        UUID leadId = inquiries.findById(id).orElseThrow().getLeadId();
        assertThat(leadId).isNotNull();
        var lead = leads.findById(leadId).orElseThrow();
        assertThat(lead.getAcquisitionSource()).isEqualTo(AcquisitionSource.META);
        assertThat(lead.getClickId()).isEqualTo("IwAR-test-click");
        assertThat(lead.getLandingPage()).isEqualTo("/lens?fbclid=IwAR-test-click");
        assertThat(lead.getCampaignId()).isEqualTo(c.getId()); // matched on utm_campaign
    }

    @Test
    void aTikTokClickIdIsRecognisedToo() {
        UUID id = service.start(phone(), "CLEAR", false, Map.of("ttclid", "tt-test-click"));
        service.verify(tokenFromLastOutbound());

        var lead = leads.findById(inquiries.findById(id).orElseThrow().getLeadId()).orElseThrow();
        assertThat(lead.getAcquisitionSource()).isEqualTo(AcquisitionSource.TIKTOK);
        assertThat(lead.getClickId()).isEqualTo("tt-test-click");
    }

    /** An organic visitor still gets a lead, just an unattributed one. */
    @Test
    void noClickIdMeansAPlainWebsiteLead() {
        UUID id = service.start(phone(), "CLEAR", false, null);
        service.verify(tokenFromLastOutbound());

        var lead = leads.findById(inquiries.findById(id).orElseThrow().getLeadId()).orElseThrow();
        assertThat(lead.getAcquisitionSource()).isEqualTo(AcquisitionSource.WEBSITE);
        assertThat(lead.getClickId()).isNull();
    }
}
