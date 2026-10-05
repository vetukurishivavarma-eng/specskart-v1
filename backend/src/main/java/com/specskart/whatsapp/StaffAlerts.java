package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fans a staff alert out to every WHATSAPP_STAFF_NUMBERS entry and keeps at it until Meta says
 * it reached the phone. Each (alert, number) is a {@link StaffAlert} row: sent at once, then
 * <ul>
 *   <li>a send Meta rejects, or a "failed" delivery status on the webhook (131042 billing,
 *       131047 outside the 24h window, ...), is retried with backoff for ~16h, alternating
 *       between the approved template with the PDF header and a plain document/text — whichever
 *       didn't just fail;</li>
 *   <li>a send with no receipt at all after {@link #RECEIPT_WAIT} is sent once more;</li>
 *   <li>"delivered"/"read" closes it. Giving up is logged as an ERROR.</li>
 * </ul>
 * The customer's uploaded prescription rides along after every successful send, best-effort.
 */
@Service
public class StaffAlerts {

    private static final Logger log = LoggerFactory.getLogger(StaffAlerts.class);

    static final Duration RECEIPT_WAIT = Duration.ofMinutes(30);
    private static final int[] BACKOFF_MINUTES = {1, 2, 5, 10, 15, 30, 60, 120, 240, 480};
    static final int MAX_ATTEMPTS = BACKOFF_MINUTES.length;
    private static final String NO_RECEIPT = "no delivery receipt from Meta";
    private static final String TEMPLATE = "TEMPLATE", PLAIN = "PLAIN", SEP = "\u001F";

    /** The customer's uploaded prescription, by public link. Images go as images (WhatsApp
     *  documents don't accept JPG/PNG), PDFs as documents. */
    public record Attachment(String url, String filename, boolean image) {}

    private final WhatsAppProvider whatsapp;
    private final AppProperties props;
    private final ServiceWindow window;
    private final StaffAlertRepository alerts;

    public StaffAlerts(WhatsAppProvider whatsapp, AppProperties props, ServiceWindow window,
                       StaffAlertRepository alerts) {
        this.whatsapp = whatsapp;
        this.props = props;
        this.window = window;
        this.alerts = alerts;
    }

    /**
     * Queue and immediately try one alert per staff number. Idempotent per (kind, refId, number):
     * a second call for the same order sends nothing.
     *
     * @param kind ORDER or LENS
     * @param lines the same lines the PDF is drawn from ("# " heading, "` " monospace)
     * @param pdfUrl absolute signed link to the PDF, or null when the API's public origin isn't configured
     * @param appLink opens the POS app on this order; appended to the non-template paths
     * @return one "number: reason" entry per send that failed right now (it stays queued for retry)
     */
    public List<String> send(String kind, UUID refId, String refNo, List<String> lines, String pdfUrl,
                             String pdfName, List<String> templateParams, String appLink, Attachment prescription) {
        List<String> numbers = Arrays.stream(String.join(",", props.whatsapp().staffNumbers()).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (numbers.isEmpty()) {
            log.error("{} {}: WHATSAPP_STAFF_NUMBERS is empty -- no staff member was told about it", kind, refNo);
            return List.of();
        }
        // The template carries this as {{4}}. The plain paths have no parameters, so without
        // appending it a staff member who gets the fallback is given no way to open the order.
        String text = whatsappText(lines) + (appLink == null || appLink.isBlank()
                ? "" : "\n\nOpen in the app: " + appLink);
        List<String> failures = new ArrayList<>();
        for (String to : numbers) {
            if (alerts.existsByKindAndRefIdAndRecipient(kind, refId, to)) continue;
            StaffAlert a = new StaffAlert();
            a.setKind(kind);
            a.setRefId(refId);
            a.setRefNo(refNo);
            a.setRecipient(to);
            a.setStatus(StaffAlert.Status.PENDING);
            a.setBody(text.length() <= 8000 ? text : text.substring(0, 8000));
            a.setPdfUrl(pdfUrl);
            a.setPdfName(pdfName);
            a.setTemplateParams(templateParams == null ? null : String.join(SEP, templateParams));
            if (prescription != null) {
                a.setRxUrl(prescription.url());
                a.setRxName(prescription.filename());
                a.setRxImage(prescription.image());
            }
            String error = attempt(a);
            if (error != null) failures.add(mask(to) + ": " + error + " -- will retry");
        }
        return failures;
    }

    /** Every minute: retry what failed, and resend once what Meta never acknowledged. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void retryDue() {
        for (StaffAlert a : alerts.findTop50ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAt(
                EnumSet.of(StaffAlert.Status.PENDING, StaffAlert.Status.SENT), Instant.now())) {
            if (a.getStatus() == StaffAlert.Status.SENT) {
                if (NO_RECEIPT.equals(a.getLastError())) {
                    // Resent once already and still silent: the webhook is likely not reaching us.
                    // Stop rather than spam the staff phone; a late "failed" status still re-queues it.
                    log.warn("staff alert {} to {}: still no delivery receipt, not resending again",
                            a.getRefNo(), mask(a.getRecipient()));
                    a.setNextAttemptAt(null);
                    alerts.save(a);
                    continue;
                }
                a.setLastError(NO_RECEIPT);
            }
            String error = attempt(a);
            if (error != null) log.warn("staff alert {} to {} retry {} failed: {}",
                    a.getRefNo(), mask(a.getRecipient()), a.getAttempts(), error);
        }
    }

    /** A delivery status from Meta's webhook. Unknown ids (customer messages etc.) are ignored. */
    public void onDeliveryStatus(String wamid, String status, String error) {
        if (wamid == null || wamid.isBlank()) return;
        alerts.findFirstByWamid(wamid).ifPresent(a -> {
            switch (status) {
                case "delivered", "read" -> {
                    a.setStatus(StaffAlert.Status.DELIVERED);
                    a.setNextAttemptAt(null);
                    a.setLastError(null);
                }
                // On WhatsApp's servers, waiting for the phone: stop the no-receipt resend and
                // wait for "delivered" or "failed", which Meta always sends eventually.
                case "sent" -> {
                    if (a.getStatus() == StaffAlert.Status.SENT) a.setNextAttemptAt(null);
                }
                case "failed" -> {
                    a.setLastError(trim("Meta: " + error));
                    scheduleRetry(a);
                    log.warn("staff alert {} to {} not delivered ({}), retrying",
                            a.getRefNo(), mask(a.getRecipient()), error);
                }
                default -> { return; }
            }
            alerts.save(a);
        });
    }

    /** One send. Saves the row either way; returns the error, or null on success. */
    private String attempt(StaffAlert a) {
        String to = a.getRecipient();
        boolean canTemplate = a.getPdfUrl() != null && props.whatsapp().staffOrderConfigured();
        // First try: template only when the 24h window is shut (inside it a plain message is free
        // and can't be blocked by billing). A retry tries whichever path didn't just fail.
        boolean template = canTemplate && (a.getLastPath() == null
                ? !window.isOpen(to)
                : PLAIN.equals(a.getLastPath()));
        a.setLastPath(template ? TEMPLATE : PLAIN);
        a.setAttempts(a.getAttempts() + 1);
        try {
            String wamid;
            if (template) {
                wamid = whatsapp.sendDocumentTemplate(to, props.whatsapp().staffOrderTemplate(),
                        props.whatsapp().followUpTemplateLang(), a.getPdfUrl(), a.getPdfName(), params(a));
            } else if (a.getPdfUrl() != null) {
                wamid = whatsapp.sendDocument(to, a.getPdfUrl(), a.getPdfName(), caption(a.getBody()));
            } else {
                wamid = whatsapp.sendText(to, a.getBody());
            }
            a.setWamid(wamid);
            a.setStatus(StaffAlert.Status.SENT);
            a.setNextAttemptAt(Instant.now().plus(RECEIPT_WAIT));
            if (!NO_RECEIPT.equals(a.getLastError())) a.setLastError(null);
            alerts.save(a);
            sendPrescription(a);
            return null;
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            a.setLastError(trim((template ? "template " + props.whatsapp().staffOrderTemplate() : "plain")
                    + ": " + reason));
            scheduleRetry(a);
            alerts.save(a);
            return reason;
        }
    }

    private void scheduleRetry(StaffAlert a) {
        if (a.getAttempts() >= MAX_ATTEMPTS) {
            a.setStatus(StaffAlert.Status.GAVE_UP);
            a.setNextAttemptAt(null);
            log.error("GAVE UP on staff alert {} {} to {} after {} attempts: {}",
                    a.getKind(), a.getRefNo(), mask(a.getRecipient()), a.getAttempts(), a.getLastError());
            return;
        }
        a.setStatus(StaffAlert.Status.PENDING);
        int i = Math.max(0, Math.min(a.getAttempts(), BACKOFF_MINUTES.length) - 1);
        a.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(BACKOFF_MINUTES[i])));
    }

    private void sendPrescription(StaffAlert a) {
        if (a.getRxUrl() == null) return;
        try {
            if (a.isRxImage()) whatsapp.sendImage(a.getRecipient(), a.getRxUrl(), "Customer's prescription");
            else whatsapp.sendDocument(a.getRecipient(), a.getRxUrl(), a.getRxName(), "Customer's prescription");
        } catch (Exception e) {
            log.warn("prescription for {} to {} failed: {}", a.getRefNo(), mask(a.getRecipient()), e.getMessage());
        }
    }

    private static List<String> params(StaffAlert a) {
        return a.getTemplateParams() == null ? List.of() : List.of(a.getTemplateParams().split(SEP, -1));
    }

    /** "# x" becomes *x* (bold), "` x" becomes monospace. */
    public static String whatsappText(List<String> lines) {
        return lines.stream()
                .map(l -> l.startsWith("# ") ? "*" + l.substring(2) + "*"
                        : l.startsWith("` ") ? "```" + l.substring(2) + "```" : l)
                .collect(Collectors.joining("\n"));
    }

    // WhatsApp caps a document caption at 1024 chars; the PDF carries everything regardless.
    private static String caption(String text) {
        return text.length() <= 1024 ? text : text.substring(0, 990) + "…\n(full details in the PDF)";
    }

    private static String trim(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static String mask(String number) {
        return number.length() <= 4 ? number : number.substring(0, number.length() - 4) + "••••";
    }
}
