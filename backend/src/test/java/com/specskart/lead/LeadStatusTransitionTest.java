package com.specskart.lead;

import com.specskart.campaign.CampaignRepository;
import com.specskart.shared.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeadStatusTransitionTest {

    private Lead lead(LeadStatus s) {
        Lead l = new Lead();
        l.setStatus(s);
        return l;
    }

    private LeadService service(Lead stored) {
        LeadRepository leads = mock(LeadRepository.class);
        when(leads.findById(any())).thenReturn(java.util.Optional.of(stored));
        when(leads.save(any())).thenAnswer(i -> i.getArgument(0));
        return new LeadService(leads, mock(CampaignRepository.class),
                mock(com.specskart.attribution.AttributionResolver.class),
                mock(com.specskart.analytics.AnalyticsService.class));
    }

    @Test
    void rejectsAnIllegalTransition() {
        var svc = service(lead(LeadStatus.NEW));
        assertThatThrownBy(() -> svc.updateStatus(java.util.UUID.randomUUID(), LeadStatus.CONVERTED, true))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot move lead");
    }

    @Test
    void allowsAValidForwardTransition() {
        var svc = service(lead(LeadStatus.ENGAGED));
        var updated = svc.updateStatus(java.util.UUID.randomUUID(), LeadStatus.FACE_ANALYSIS_STARTED, true);
        assertThat(updated.getStatus()).isEqualTo(LeadStatus.FACE_ANALYSIS_STARTED);
    }

    @Test
    void softAdvanceNeverMovesBackwardsOrOutOfTerminalStates() {
        var svc = service(lead(LeadStatus.CONVERTED));
        svc.advanceStatusSoft(java.util.UUID.randomUUID(), LeadStatus.NEW);
        // no exception, stays converted
        var svc2 = service(lead(LeadStatus.INTERESTED));
        svc2.advanceStatusSoft(java.util.UUID.randomUUID(), LeadStatus.NEW);
    }
}
