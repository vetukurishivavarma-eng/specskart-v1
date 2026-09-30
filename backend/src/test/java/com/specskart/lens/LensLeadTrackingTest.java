package com.specskart.lens;

import com.specskart.analytics.LeadEvent;
import com.specskart.analytics.LeadEventRepository;
import com.specskart.lead.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class LensLeadTrackingTest {

    @Autowired LensInquiryService service;
    @Autowired LensInquiryRepository inquiries;
    @Autowired LeadRepository leads;
    @Autowired LeadEventRepository events;

    @Test
    void aPersonalLinkSkipsTheNumberStepAndEveryStepLandsOnTheLead() {
        // a walk-in-free, already-known customer: first order verifies them and creates the lead
        String phone = "0976" + (100000 + (int) (Math.random() * 800000));
        UUID first = service.start(phone, "CLEAR", false, null);
        var q0 = inquiries.findById(first).orElseThrow();
        q0.setPhoneVerifiedAt(java.time.Instant.now());
        inquiries.save(q0);
        UUID seed = service.start(phone, "CLEAR", false, null); // known number -> lead created
        var lead = leads.findById(inquiries.findById(seed).orElseThrow().getLeadId()).orElseThrow();

        String url = service.personalLink(lead);
        var p = org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        var link = new LensDtos.PersonalLink(UUID.fromString(p.getFirst("l")),
                Long.parseLong(p.getFirst("exp")), p.getFirst("sig"));

        assertThat(service.openLink(link).maskedNumber()).endsWith(phone.substring(phone.length() - 4));

        // no phone typed at all -- the link stands in for it
        UUID id = service.start(null, "PHOTOCHROMATIC", true, null, link);
        var q = inquiries.findById(id).orElseThrow();
        assertThat(q.isPhoneVerified()).isTrue();
        assertThat(q.getLeadId()).isEqualTo(lead.getId());

        service.update(id, new LensDtos.UpdateDetails("Nachi", 40, "F", new BigDecimal("-1.25"), new BigDecimal("-1.00"),
                null, null, null, null, null, null));
        service.quote(id);
        service.submit(id);

        var types = events.findByLeadIdOrderByCreatedAtAsc(lead.getId()).stream().map(LeadEvent::getEventType).map(Enum::name).toList();
        assertThat(types).contains("LENS_LINK_OPENED", "LENS_STARTED", "LENS_RX_ENTERED", "LENS_QUOTED", "LENS_ORDERED");
        assertThat(events.findByLeadIdOrderByCreatedAtAsc(lead.getId()).stream()
                .filter(e -> e.getEventType().name().equals("LENS_RX_ENTERED")).findFirst().orElseThrow()
                .getMetadata().get("rx").toString()).contains("SPH -1.25");

        assertThat(service.stagesFor(java.util.List.of(lead.getId()))).containsEntry(lead.getId(), "Ordered");
        assertThat(service.ordersForLead(lead.getId()).get(0)).containsEntry("sphRight", new BigDecimal("-1.25"));
    }

    @Test
    void aTamperedLinkIsRefused() {
        var bad = new LensDtos.PersonalLink(UUID.randomUUID(), java.time.Instant.now().getEpochSecond() + 999, "nope");
        assertThatThrownBy(() -> service.openLink(bad)).hasMessageContaining("expired");
    }
}
