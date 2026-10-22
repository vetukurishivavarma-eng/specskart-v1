package com.specskart.whatsapp;

import com.specskart.lead.FollowUpState;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin-triggered "send this to a bunch of leads at once" — new arrivals, a weekend sale.
 * Deliberately manual and bounded, never scheduled or automatic: an approved template is
 * mandatory (the only thing Meta delivers to a cold lead at scale), opted-out leads are
 * always excluded, and one call is hard-capped so a typo can't blast the whole lead base.
 */
@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);
    private static final int HARD_CAP = 500;

    /** optedInOnly: just customers who explicitly signed up for offers (walk-in QR).
     *  leadIds: only these customers (staff ticked them); null = everyone matching. */
    public record Filter(String faceShape, boolean excludeConverted, boolean optedInOnly, java.util.Collection<UUID> leadIds) {
        public Filter(String faceShape, boolean excludeConverted, boolean optedInOnly) {
            this(faceShape, excludeConverted, optedInOnly, null);
        }
    }
    public record SendResult(int sent, int failed, int skippedOverCap) {}

    private final LeadRepository leads;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;
    private final ServiceWindow window;

    public BroadcastService(LeadRepository leads, WhatsAppProvider whatsapp, WhatsAppMessageRepository messages,
                            ServiceWindow window) {
        this.window = window;
        this.leads = leads;
        this.whatsapp = whatsapp;
        this.messages = messages;
    }

    /** Matching leads who haven't had this template yet: sending again reaches the rest, never the same person twice (each send is paid). */
    private List<Lead> candidates(Filter f, String templateName) {
        return matching(f, templateName == null || templateName.isBlank() ? null : "broadcast:" + templateName.trim()).toList();
    }

    /** Leads matching the filter who don't already have a message with this body. */
    private java.util.stream.Stream<Lead> matching(Filter f, String sentBody) {
        java.util.Set<UUID> had = sentBody == null ? java.util.Set.of() : messages.leadIdsWithBody(sentBody);
        List<Lead> pool = f.leadIds() == null
                ? leads.findTop1000ByArchivedAtIsNullOrderByLastContactAtDesc()
                : leads.findAllById(f.leadIds());
        return pool.stream()
                .filter(l -> l.getArchivedAt() == null)
                .filter(l -> !had.contains(l.getId()))
                .filter(l -> l.getFollowUpState() != FollowUpState.OPTED_OUT)
                .filter(l -> waId(l) != null)
                .filter(l -> f.faceShape() == null || f.faceShape().equalsIgnoreCase(l.getFaceShape()))
                .filter(l -> !f.excludeConverted() || l.getStatus() != LeadStatus.CONVERTED)
                .filter(l -> !f.optedInOnly() || l.getMarketingOptInAt() != null);
    }

    // ---- a typed message ----------------------------------------------------------------------

    /**
     * A message staff type themselves. WhatsApp only delivers free text within 24 hours of the
     * customer's last message (and it's free then), so it goes to exactly those customers, and
     * never twice to the same person.
     */
    private List<Lead> openChats(Filter f, String text) {
        return matching(f, text.trim()).filter(l -> window.isOpen(waId(l))).toList();
    }

    public int previewText(Filter f, String text) {
        return text == null || text.isBlank() ? 0 : openChats(f, text).size();
    }

    @Transactional
    public SendResult sendText(Filter f, String text, int limit) {
        if (text == null || text.isBlank()) throw ApiException.badRequest("TEXT_REQUIRED", "Type the message to send.");
        if (text.length() > 1000) throw ApiException.badRequest("TEXT_TOO_LONG", "Keep the message under 1000 characters.");
        text = text.trim();
        List<Lead> all = openChats(f, text);
        List<Lead> targets = all.stream().limit(Math.min(limit <= 0 ? 200 : limit, HARD_CAP)).toList();
        int sent = 0, failed = 0;
        for (Lead lead : targets) {
            try {
                whatsapp.sendText(waId(lead), text);
                WhatsAppMessage m = new WhatsAppMessage(); // the text itself, so it shows in the chat
                m.setLeadId(lead.getId());
                m.setDirection("OUTBOUND");
                m.setMessageType("broadcast");
                m.setBody(text);
                m.setStatus("sent");
                messages.save(m);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("text broadcast failed for lead {}: {}", lead.getId(), e.getMessage());
            }
        }
        log.info("text broadcast: {} sent, {} failed, {} over cap", sent, failed, all.size() - targets.size());
        return new SendResult(sent, failed, all.size() - targets.size());
    }

    /** How many leads this filter would reach — call before send() to sanity-check the blast. */
    public int previewCount(Filter f, String templateName) {
        return candidates(f, templateName).size();
    }

    @Transactional
    public SendResult send(Filter f, String templateName, String languageCode, String headerImageUrl,
                           List<String> bodyParams, int limit) {
        return send(f, templateName, languageCode, headerImageUrl, bodyParams, limit, null);
    }

    /** freeText set: customers whose 24h chat is open get it as plain text (free); only the rest get the paid template. */
    @Transactional
    public SendResult send(Filter f, String templateName, String languageCode, String headerImageUrl,
                           List<String> bodyParams, int limit, String freeText) {
        if (templateName == null || templateName.isBlank()) {
            throw ApiException.badRequest("TEMPLATE_REQUIRED",
                    "Name an approved WhatsApp template — that's the only thing Meta delivers to a cold lead at scale.");
        }
        templateName = templateName.trim();
        List<Lead> all = candidates(f, templateName);
        int cap = Math.min(limit <= 0 ? 200 : limit, HARD_CAP);
        List<Lead> targets = all.stream().limit(cap).toList();

        int sent = 0, failed = 0;
        for (Lead lead : targets) {
            try {
                if (freeText != null && !freeText.isBlank() && window.isOpen(waId(lead))) {
                    whatsapp.sendText(waId(lead), freeText.trim());
                } else {
                    whatsapp.sendMediaTemplate(waId(lead), templateName, languageCode, headerImageUrl, bodyParams);
                }
                logOutbound(lead.getId(), templateName);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("broadcast send failed for lead {}: {}", lead.getId(), e.getMessage());
            }
        }
        int skipped = Math.max(0, all.size() - targets.size());
        log.info("broadcast '{}': {} sent, {} failed, {} skipped over cap", templateName, sent, failed, skipped);
        return new SendResult(sent, failed, skipped);
    }

    private static String waId(Lead lead) {
        return lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
    }

    private void logOutbound(UUID leadId, String templateName) {
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(leadId);
        m.setDirection("OUTBOUND");
        m.setMessageType("broadcast");
        m.setBody("broadcast:" + templateName);
        m.setStatus("sent");
        messages.save(m);
    }
}
