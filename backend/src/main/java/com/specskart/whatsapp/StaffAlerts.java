package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fans a staff alert out to every WHATSAPP_STAFF_NUMBERS entry. Preference order: the approved
 * staff template with the PDF as its header (the only thing Meta delivers outside the 24h window),
 * else the PDF as a plain document captioned with the full details, else plain text — then the
 * customer's own uploaded prescription, if there is one. Best-effort per number.
 *
 * <p>A template send that Meta rejects (missing, paused, wrong locale) falls through to the
 * plain document rather than losing the alert, and is reported in the returned failures so the
 * misconfiguration is visible instead of silent.
 */
@Service
public class StaffAlerts {

    private static final Logger log = LoggerFactory.getLogger(StaffAlerts.class);

    /** The customer's uploaded prescription, by public link. Images go as images (WhatsApp
     *  documents don't accept JPG/PNG), PDFs as documents. */
    public record Attachment(String url, String filename, boolean image) {}

    private final WhatsAppProvider whatsapp;
    private final AppProperties props;

    public StaffAlerts(WhatsAppProvider whatsapp, AppProperties props) {
        this.whatsapp = whatsapp;
        this.props = props;
    }

    /**
     * @param lines the same lines the PDF is drawn from ("# " heading, "` " monospace)
     * @param pdfUrl absolute signed link to the PDF, or null when the API's public origin isn't configured
     * @return one "number: reason" entry per failed send, for the caller's timeline
     */
    public List<String> send(List<String> lines, String pdfUrl, String pdfName,
                             List<String> templateParams, Attachment prescription) {
        List<String> failures = new ArrayList<>();
        String text = whatsappText(lines);
        for (String raw : props.whatsapp().staffNumbers()) {
            String to = raw.trim();
            if (to.isEmpty()) continue;
            try {
                boolean sent = false;
                if (pdfUrl != null && props.whatsapp().staffOrderConfigured()) {
                    try {
                        whatsapp.sendDocumentTemplate(to, props.whatsapp().staffOrderTemplate(),
                                props.whatsapp().followUpTemplateLang(), pdfUrl, pdfName, templateParams);
                        sent = true;
                    } catch (Exception e) {
                        // A template that is missing, paused or in the wrong locale used to lose the
                        // alert outright. Drop to a plain document, which still reaches anyone who
                        // messaged us in the last 24h, and say so in the caller's timeline.
                        log.warn("staff order template {} failed for {}, falling back to a plain document: {}",
                                props.whatsapp().staffOrderTemplate(), mask(to), e.getMessage());
                        failures.add(mask(to) + " (template " + props.whatsapp().staffOrderTemplate()
                                + "): " + e.getMessage());
                    }
                }
                if (!sent) {
                    if (pdfUrl != null) whatsapp.sendDocument(to, pdfUrl, pdfName, caption(text));
                    else whatsapp.sendText(to, text);
                }
                if (prescription != null) {
                    if (prescription.image()) whatsapp.sendImage(to, prescription.url(), "Customer's prescription");
                    else whatsapp.sendDocument(to, prescription.url(), prescription.filename(), "Customer's prescription");
                }
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("staff alert to {} failed: {}", mask(to), reason);
                failures.add(mask(to) + ": " + reason);
            }
        }
        return failures;
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

    private static String mask(String number) {
        return number.length() <= 4 ? number : number.substring(0, number.length() - 4) + "••••";
    }
}
