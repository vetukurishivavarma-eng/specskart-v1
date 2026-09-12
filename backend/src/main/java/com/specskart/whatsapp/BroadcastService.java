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

    public record Filter(String faceShape, boolean excludeConverted) {}
    public record SendResult(int sent, int failed, int skippedOverCap) {}

    private final LeadRepository leads;
    private final WhatsAppProvider whatsapp;
    private final WhatsAppMessageRepository messages;

    public BroadcastService(LeadRepository leads, WhatsAppProvider whatsapp, WhatsAppMessageRepository messages) {
        this.leads = leads;
        this.whatsapp = whatsapp;
        this.messages = messages;
    }

    private List<Lead> candidates(Filter f) {
        return leads.findTop1000ByArchivedAtIsNullOrderByLastContactAtDesc().stream()
                .filter(l -> l.getFollowUpState() != FollowUpState.OPTED_OUT)
                .filter(l -> waId(l) != null)
                .filter(l -> f.faceShape() == null || f.faceShape().equalsIgnoreCase(l.getFaceShape()))
                .filter(l -> !f.excludeConverted() || l.getStatus() != LeadStatus.CONVERTED)
                .toList();
    }

    /** How many leads this filter would reach — call before send() to sanity-check the blast. */
    public int previewCount(Filter f) {
        return candidates(f).size();
    }

    @Transactional
    public SendResult send(Filter f, String templateName, String languageCode, String headerImageUrl,
                           List<String> bodyParams, int limit) {
        if (templateName == null || templateName.isBlank()) {
            throw ApiException.badRequest("TEMPLATE_REQUIRED",
                    "Name an approved WhatsApp template — that's the only thing Meta delivers to a cold lead at scale.");
        }
        List<Lead> all = candidates(f);
        int cap = Math.min(limit <= 0 ? 200 : limit, HARD_CAP);
        List<Lead> targets = all.stream().limit(cap).toList();

        int sent = 0, failed = 0;
        for (Lead lead : targets) {
            try {
                whatsapp.sendMediaTemplate(waId(lead), templateName, languageCode, headerImageUrl, bodyParams);
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
