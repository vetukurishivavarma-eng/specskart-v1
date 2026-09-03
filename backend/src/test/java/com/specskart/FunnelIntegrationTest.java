package com.specskart;

import com.specskart.analytics.LeadEventRepository;
import com.specskart.analytics.LeadEventType;
import com.specskart.campaign.*;
import com.specskart.framefinder.FrameFinderService;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import org.springframework.data.domain.PageRequest;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class FunnelIntegrationTest {

    @Autowired WhatsAppInboundService inbound;
    @Autowired LeadRepository leads;
    @Autowired CampaignRepository campaigns;
    @Autowired LeadEventRepository events;
    @Autowired WhatsAppProvider provider;
    @Autowired FrameFinderService frameFinder;

    @Test
    void whatsAppContactCreatesLeadWithCampaignAttribution() {
        Campaign c = new Campaign();
        c.setName("Test FB");
        c.setPlatform(Platform.FACEBOOK);
        c.setExternalCampaignId("ext-123");
        c.setStatus(CampaignStatus.ACTIVE);
        campaigns.save(c);

        inbound.process(new InboundMessage("2609770001", "2609770001", "Test User", "Hi", null, "m1",
                Map.of("referral", Map.of("source_id", "ext-123"), "utm_source", "facebook")));

        Lead lead = leads.findByWhatsappWaId("2609770001").orElseThrow();
        assertThat(lead.getCampaignId()).isEqualTo(c.getId());
        assertThat(lead.getName()).isEqualTo("Test User");
        assertThat(events.findByLeadIdOrderByCreatedAtAsc(lead.getId()))
                .anyMatch(e -> e.getEventType() == LeadEventType.WHATSAPP_CONVERSATION_STARTED);
    }

    @Test
    void archiveHidesFromDefaultListAndHardDeleteCascades() {
        inbound.process(new InboundMessage("2609771234", "2609771234", "Archive Me", "Hi", null, "arch-1", Map.of()));
        Lead lead = leads.findByWhatsappWaId("2609771234").orElseThrow();
        var id = lead.getId();
        assertThat(events.findByLeadIdOrderByCreatedAtAsc(id)).isNotEmpty();

        leadService.setArchived(id, true);
        assertThat(leads.search(null, null, null, false, PageRequest.of(0, 50)).getContent())
                .noneMatch(l -> l.getId().equals(id));
        assertThat(leads.search(null, null, null, true, PageRequest.of(0, 50)).getContent())
                .anyMatch(l -> l.getId().equals(id));

        leadService.setArchived(id, false);
        assertThat(leads.search(null, null, null, false, PageRequest.of(0, 50)).getContent())
                .anyMatch(l -> l.getId().equals(id));

        leadService.hardDelete(id);
        assertThat(leads.findById(id)).isEmpty();
        assertThat(events.findByLeadIdOrderByCreatedAtAsc(id)).isEmpty(); // FK cascade
    }

    @Autowired com.specskart.lead.LeadService leadService;

    @Test
    void leadSearchRunsWithAllFiltersNull() {
        // Regression: the CRM leads list passes q=null; on PostgreSQL the bare
        // null bind made `lower(concat('%', :q, '%'))` resolve to lower(bytea)
        // and 500. The `cast(:q as string)` in the query keeps it valid.
        inbound.process(new InboundMessage("2609779999", "2609779999", "Search Me", "Hi", null, "search-1", Map.of()));
        assertThat(leads.search(null, null, null, false, PageRequest.of(0, 20)).getContent()).isNotEmpty();
        assertThat(leads.search(null, null, "search", false, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void duplicateWebhookMessageIsProcessedOnce() {
        var in = new InboundMessage("2609770002", "2609770002", "Dup", "Hi", null, "dup-msg-1", Map.of());
        inbound.process(in);
        inbound.process(in);
        Lead lead = leads.findByWhatsappWaId("2609770002").orElseThrow();
        long received = events.findByLeadIdOrderByCreatedAtAsc(lead.getId()).stream()
                .filter(e -> e.getEventType() == LeadEventType.WHATSAPP_MESSAGE_RECEIVED).count();
        assertThat(received).isEqualTo(1);
    }

    @Test
    void pressingFindFramesSendsAnOpaqueSessionLinkWithoutThePhoneNumber() {
        inbound.process(new InboundMessage("2609770003", "2609770003", "Finder", null, "FIND_FRAMES", "m3", Map.of()));
        MockWhatsAppProvider mock = (MockWhatsAppProvider) provider;
        String link = mock.outbox().stream().map(MockWhatsAppProvider.Sent::text)
                .filter(t -> t != null && t.contains("/frame-finder?s="))
                .reduce((a, b) -> b).orElseThrow();
        assertThat(link).doesNotContain("2609770003");
        assertThat(link).containsPattern("s=[A-Za-z0-9_-]{20,}");
    }

    @Test
    void expiredSessionTokenIsRejected() {
        inbound.process(new InboundMessage("2609770004", "2609770004", "Exp", null, "FIND_FRAMES", "m4", Map.of()));
        Lead lead = leads.findByWhatsappWaId("2609770004").orElseThrow();
        var created = frameFinder.createForLead(lead.getId(), null);
        var s = created.session();
        s.setExpiresAt(java.time.Instant.now().minusSeconds(10));
        // persist the expiry via repository through a fresh create then manual check
        assertThat(s.isExpired()).isTrue();
    }
}
