package com.specskart.lead;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Drives the automated WhatsApp nurture sequence. Every hour:
 *  1. enrol any cold, analysed, un-enrolled lead from the last 30 days (one-time catch-up)
 *  2. send the next due touch for every ACTIVE lead
 * Each lead is handled in its own transaction so one failure can't stall the rest.
 */
@Component
class LeadFollowUpJob {

    private static final Logger log = LoggerFactory.getLogger(LeadFollowUpJob.class);

    private final LeadRepository leads;
    private final LeadFollowUpService service;
    private final AppProperties props;

    LeadFollowUpJob(LeadRepository leads, LeadFollowUpService service, AppProperties props) {
        this.leads = leads;
        this.service = service;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT4M")
    public void run() {
        if (!props.whatsapp().nurtureOn()) return;

        List<Lead> fresh = leads.findTop100ByFollowUpStateIsNullAndArchivedAtIsNullAndStatusInAndCreatedAtAfter(
                LeadFollowUpService.ENROLLABLE_STATUSES, Instant.now().minus(30, ChronoUnit.DAYS));
        for (Lead lead : fresh) {
            try { service.enroll(lead.getId()); }
            catch (Exception e) { log.warn("nurture enrol failed for lead {}: {}", lead.getId(), e.getMessage()); }
        }

        List<Lead> due = leads.findTop50ByFollowUpStateAndFollowUpNextAtLessThanEqualOrderByFollowUpNextAtAsc(
                FollowUpState.ACTIVE, Instant.now());
        for (Lead lead : due) {
            try { service.runDueTouch(lead.getId()); }
            catch (Exception e) { log.warn("nurture touch failed for lead {}: {}", lead.getId(), e.getMessage()); }
        }
        if (!fresh.isEmpty() || !due.isEmpty()) {
            log.info("nurture pass: enrolled {}, sent up to {}", fresh.size(), due.size());
        }
    }
}
