package com.specskart;

import com.specskart.lead.FollowUpState;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadFollowUpService;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class NurtureSequenceTest {

    @Autowired LeadFollowUpService followUp;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppMessageRepository waMessages;
    @Autowired WhatsAppProvider provider;

    private UUID enrolledLead() {
        Lead l = new Lead();
        l.setWhatsappWaId("26097" + (100000 + (int) (Math.random() * 800000)));
        l.setWhatsappNumber(l.getWhatsappWaId());
        l.setName("Nurture Tester");
        l.setStatus(LeadStatus.FACE_ANALYSIS_COMPLETED);
        l.setFaceShape("OVAL");
        l = leads.save(l);
        followUp.enroll(l.getId());
        return l.getId();
    }

    private void makeDue(UUID id) {
        Lead l = leads.findById(id).orElseThrow();
        l.setFollowUpNextAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        leads.save(l);
    }

    @Test
    void enrolmentSchedulesTheFirstTouch() {
        Lead l = leads.findById(enrolledLead()).orElseThrow();
        assertThat(l.getFollowUpState()).isEqualTo(FollowUpState.ACTIVE);
        assertThat(l.getFollowUpStep()).isZero();
        assertThat(l.getFollowUpNextAt()).isAfter(Instant.now());
    }

    @Test
    void eachDueTouchSendsAMessageAndAdvancesUntilDone() {
        UUID id = enrolledLead();
        int before = ((MockWhatsAppProvider) provider).outbox().size();

        for (int i = 0; i < LeadFollowUpService.stepCount(); i++) {
            makeDue(id);
            followUp.runDueTouch(id);
        }

        assertThat(((MockWhatsAppProvider) provider).outbox().size()).isGreaterThan(before);
        assertThat(waMessages.findByLeadIdOrderByCreatedAtAsc(id))
                .filteredOn(m -> "OUTBOUND".equals(m.getDirection()) && m.getBody().startsWith("nurture:"))
                .hasSize(LeadFollowUpService.stepCount());
        assertThat(leads.findById(id).orElseThrow().getFollowUpState()).isEqualTo(FollowUpState.DONE);
    }

    @Test
    void stopKeywordOptsOut() {
        UUID id = enrolledLead();
        followUp.onInbound(id, "please STOP messaging me");
        assertThat(leads.findById(id).orElseThrow().getFollowUpState()).isEqualTo(FollowUpState.OPTED_OUT);
    }

    @Test
    void aPurchaseStopsTheSequence() {
        UUID id = enrolledLead();
        followUp.onConverted(id);
        assertThat(leads.findById(id).orElseThrow().getFollowUpState()).isEqualTo(FollowUpState.CONVERTED);
    }
}
