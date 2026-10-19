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
 * Hourly. Starts at most {@link #NEW_PER_DAY} people a day on the checkup reminder, so a first
 * run against the whole lead base trickles out instead of blasting it (the fast way to get a
 * WhatsApp number's quality rating dropped), then sends whatever touches are due.
 */
@Component
class EyeCheckupReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EyeCheckupReminderJob.class);
    static final int NEW_PER_DAY = 50;

    private final LeadRepository leads;
    private final EyeCheckupReminderService service;
    private final AppProperties props;

    EyeCheckupReminderJob(LeadRepository leads, EyeCheckupReminderService service, AppProperties props) {
        this.leads = leads;
        this.service = service;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT13M")
    public void run() {
        if (!props.whatsapp().nurtureOn() || !props.whatsapp().checkupConfigured()) return;
        Instant now = Instant.now();

        int room = (int) Math.max(0, NEW_PER_DAY - leads.countByCheckupStartedAtAfter(now.minus(1, ChronoUnit.DAYS)));
        List<Lead> fresh = room == 0 ? List.of()
                : leads.findReadyForCheckupReminder(now.minus(1, ChronoUnit.DAYS), PageRequest.of(0, room));
        for (Lead lead : fresh) {
            try { service.start(lead.getId()); }
            catch (Exception e) { log.warn("checkup reminder start failed for lead {}: {}", lead.getId(), e.getMessage()); }
        }

        List<Lead> due = leads.findTop100ByCheckupNextAtLessThanEqualOrderByCheckupNextAtAsc(now.plusSeconds(60));
        for (Lead lead : due) {
            try { service.runDue(lead.getId()); }
            catch (Exception e) { log.warn("checkup reminder failed for lead {}: {}", lead.getId(), e.getMessage()); }
        }
        if (!fresh.isEmpty() || !due.isEmpty()) log.info("checkup reminder pass: started {}, due {}", fresh.size(), due.size());
    }
}
