package com.specskart.lens;

import com.specskart.config.AppProperties;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A verified lens inquiry that never reached SUBMITTED — the shopper picked a lens type,
 * verified their number, then stalled on the prescription form. One WhatsApp nudge, sent
 * once, ~2h after they started (long enough that it's not "still filling it in", short
 * enough that the ad that brought them here is still fresh).
 */
@Component
class LensInquiryFollowUpJob {

    private static final Logger log = LoggerFactory.getLogger(LensInquiryFollowUpJob.class);
    private static final int DELAY_HOURS = 2;

    private final LensInquiryRepository inquiries;
    private final WhatsAppProvider whatsapp;
    private final AppProperties props;

    LensInquiryFollowUpJob(LensInquiryRepository inquiries, WhatsAppProvider whatsapp, AppProperties props) {
        this.inquiries = inquiries;
        this.whatsapp = whatsapp;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT5M")
    @Transactional
    public void nudge() {
        Instant cutoff = Instant.now().minus(DELAY_HOURS, ChronoUnit.HOURS);
        var due = inquiries.findTop50ByStatusAndNudgedAtIsNullAndCreatedAtBefore("VERIFIED", cutoff);
        for (LensInquiry q : due) {
            q.setNudgedAt(Instant.now());
            inquiries.save(q);
            try {
                String link = props.frontendBaseUrl() + "/lens?resume=" + q.getId();
                String lens = "PHOTOCHROMATIC".equals(q.getLensType()) ? "Photochromatic" : "Clear";
                whatsapp.sendText(q.getWaId(), "Still want your " + lens
                        + (q.isBlueBlock() ? " + blue block" : "") + " lenses? Finish here — takes a minute:\n" + link);
            } catch (Exception e) {
                log.warn("lens follow-up failed for inquiry {}: {}", q.getId(), e.getMessage());
            }
        }
        if (!due.isEmpty()) log.info("lens inquiry follow-up: nudged {}", due.size());
    }
}
