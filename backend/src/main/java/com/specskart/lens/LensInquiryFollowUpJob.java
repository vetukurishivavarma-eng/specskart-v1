package com.specskart.lens;

import com.specskart.config.AppProperties;
import com.specskart.order.OrderNotificationService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A lens inquiry that never reached SUBMITTED. Two places a shopper stalls:
 * <ul>
 *   <li>VERIFIED — picked a lens type, verified their number, never finished the prescription.</li>
 *   <li>PRICED — got as far as seeing the price and stopped. The hotter of the two, and since
 *       V39 it is also where they are first asked for a delivery address, so the nudge quotes
 *       the price back to them.</li>
 * </ul>
 * One WhatsApp per inquiry either way, ~2h after they started (long enough that it isn't "still
 * filling it in", short enough that the ad that brought them here is still fresh). `nudgedAt`
 * is what makes it once-only, so a shopper chased at VERIFIED is not chased again at PRICED.
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
        int sent = 0;
        // PRICED first: an inquiry is only ever in one of these, and the hotter stage wins.
        for (String status : List.of("PRICED", "VERIFIED")) {
            for (LensInquiry q : inquiries.findTop50ByStatusAndNudgedAtIsNullAndCreatedAtBefore(status, cutoff)) {
                q.setNudgedAt(Instant.now());
                inquiries.save(q);
                try {
                    whatsapp.sendText(q.getWaId(), message(q));
                    sent++;
                } catch (Exception e) {
                    log.warn("lens follow-up failed for inquiry {}: {}", q.getId(), e.getMessage());
                }
            }
        }
        if (sent > 0) log.info("lens inquiry follow-up: nudged {}", sent);
    }

    private String message(LensInquiry q) {
        String link = props.frontendBaseUrl() + "/lens?resume=" + q.getId();
        String lens = ("PHOTOCHROMATIC".equals(q.getLensType()) ? "Photochromatic" : "Clear")
                + (q.isBlueBlock() ? " + blue block" : "");
        if ("PRICED".equals(q.getStatus()) && q.getPriceMinor() != null) {
            return "Your " + lens + " lenses come to "
                    + OrderNotificationService.money(q.getPriceMinor(), q.getCurrency())
                    + ".\n\nAll that's left is where to deliver them — finish here:\n" + link;
        }
        return "Still want your " + lens + " lenses? Finish here — takes a minute:\n" + link;
    }
}
