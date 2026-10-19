package com.specskart.lead;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.config.AppProperties;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Come in for an eye checkup" reminder to everyone who has ever messaged the business number,
 * every {@link #GAP} up to {@link #MAX_TOUCHES} times. Stops on any reply, an order, or STOP.
 *
 * <p>Template-only on purpose: these people are outside the 24h window by definition, so plain
 * text would be accepted by Meta and never delivered. No template configured = the job is off.
 */
@Service
public class EyeCheckupReminderService {

    private static final Logger log = LoggerFactory.getLogger(EyeCheckupReminderService.class);

    static final Duration GAP = Duration.ofDays(2);
    static final int MAX_TOUCHES = 4;

    private final LeadRepository leads;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final AnalyticsService analytics;
    private final AppProperties props;

    public EyeCheckupReminderService(LeadRepository leads, WhatsAppProvider whatsapp,
                                     WhatsAppMessageRepository messages, AnalyticsService analytics,
                                     AppProperties props) {
        this.leads = leads;
        this.whatsapp = whatsapp;
        this.messages = messages;
        this.analytics = analytics;
        this.props = props;
    }

    /** Put a lead into the sequence; the first touch goes out on the same pass. */
    @Transactional
    public void start(UUID leadId) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.getCheckupStartedAt() != null) return;
        lead.setCheckupStartedAt(Instant.now());
        lead.setCheckupStep(0);
        // a second back: the stored time is rounded to the microsecond and could otherwise
        // land just after the runDue() that follows in the same pass, skipping the first touch
        lead.setCheckupNextAt(Instant.now().minusSeconds(1));
        leads.save(lead);
    }

    /** Send the due touch, or end the sequence if they replied, ordered or opted out since it began. */
    @Transactional
    public void runDue(UUID leadId) {
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.getCheckupNextAt() == null || lead.getCheckupNextAt().isAfter(Instant.now())) return;
        Instant started = lead.getCheckupStartedAt();
        boolean replied = messages.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(lead.getId(), "INBOUND")
                .map(m -> m.getCreatedAt().isAfter(started)).orElse(false);
        if (replied || lead.getFollowUpState() == FollowUpState.OPTED_OUT || lead.getArchivedAt() != null
                || lead.getCheckupStep() >= MAX_TOUCHES || leads.hasOrderSince(lead.getId(), started)) {
            finish(lead);
            return;
        }
        try {
            whatsapp.sendTemplate(lead.getWhatsappWaId(), props.whatsapp().checkupTemplate(),
                    props.whatsapp().followUpTemplateLang(), List.of(firstName(lead), link()));
        } catch (Exception e) {
            // a number that blocked us or left WhatsApp must not be retried every hour forever
            log.warn("checkup reminder to lead {} failed: {}", lead.getId(), e.getMessage());
            finish(lead);
            return;
        }
        logOutbound(lead.getId());
        analytics.record(LeadEventType.CHECKUP_REMINDER_SENT, lead.getId(), null);
        int step = lead.getCheckupStep() + 1;
        lead.setCheckupStep(step);
        lead.setCheckupNextAt(step >= MAX_TOUCHES ? null : Instant.now().plus(GAP));
        leads.save(lead);
    }

    private void finish(Lead lead) {
        lead.setCheckupNextAt(null);
        leads.save(lead);
    }

    private String link() {
        String l = props.whatsapp().checkupLink();
        return l == null || l.isBlank() ? props.frontendBaseUrl() + "/store" : l.trim();
    }

    private static String firstName(Lead lead) {
        String n = lead.getName();
        if (n == null || n.isBlank()) return "there";
        return n.trim().split("\\s+")[0];
    }

    private void logOutbound(UUID leadId) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType("template");
        m.setBody(props.whatsapp().checkupTemplate());
        m.setStatus("sent");
        messages.save(m);
    }
}
