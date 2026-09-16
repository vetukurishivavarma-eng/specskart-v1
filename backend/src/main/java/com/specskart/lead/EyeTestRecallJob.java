package com.specskart.lead;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Once a day, nudge everyone whose last delivered order is a year old. Daily rather than hourly
 * because the thing being measured is a year — and capped per pass so a first run against a long
 * back catalogue trickles out instead of firing hundreds of messages at once (which is also how
 * you get a WhatsApp number flagged).
 */
@Component
class EyeTestRecallJob {

    private static final Logger log = LoggerFactory.getLogger(EyeTestRecallJob.class);
    private static final int MAX_PER_PASS = 50;

    private final LeadRepository leads;
    private final EyeTestRecallService service;
    private final AppProperties props;

    EyeTestRecallJob(LeadRepository leads, EyeTestRecallService service, AppProperties props) {
        this.leads = leads;
        this.service = service;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "P1D", initialDelayString = "PT9M")
    public void run() {
        // Shares the nurture master switch: one "stop messaging my customers" lever, not two.
        if (!props.whatsapp().nurtureOn()) return;

        Instant cutoff = Instant.now().minus(props.whatsapp().recallMonths() * 30L, ChronoUnit.DAYS);
        List<Lead> due = leads.findDueForEyeTestRecall(cutoff, PageRequest.of(0, MAX_PER_PASS));
        for (Lead lead : due) {
            try { service.recall(lead.getId()); }
            catch (Exception e) { log.warn("eye-test recall failed for lead {}: {}", lead.getId(), e.getMessage()); }
        }
        if (!due.isEmpty()) log.info("eye-test recall pass: nudged {}", due.size());
    }
}
