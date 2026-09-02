package com.specskart.lead;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.attribution.AttributionContext;
import com.specskart.attribution.AttributionResolver;
import com.specskart.campaign.Campaign;
import com.specskart.campaign.CampaignRepository;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private static final Map<LeadStatus, Set<LeadStatus>> ALLOWED = new EnumMap<>(LeadStatus.class);
    static {
        ALLOWED.put(LeadStatus.NEW, Set.of(LeadStatus.CONTACTED, LeadStatus.ENGAGED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.CONTACTED, Set.of(LeadStatus.ENGAGED, LeadStatus.FOLLOW_UP, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.ENGAGED, Set.of(LeadStatus.FACE_ANALYSIS_STARTED, LeadStatus.INTERESTED,
                LeadStatus.FOLLOW_UP, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.FACE_ANALYSIS_STARTED, Set.of(LeadStatus.FACE_ANALYSIS_COMPLETED,
                LeadStatus.ENGAGED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.FACE_ANALYSIS_COMPLETED, Set.of(LeadStatus.INTERESTED, LeadStatus.FOLLOW_UP,
                LeadStatus.CONVERTED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.INTERESTED, Set.of(LeadStatus.FOLLOW_UP, LeadStatus.CONVERTED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.FOLLOW_UP, Set.of(LeadStatus.INTERESTED, LeadStatus.CONVERTED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.CONVERTED, Set.of());
        ALLOWED.put(LeadStatus.LOST, Set.of(LeadStatus.ENGAGED, LeadStatus.FOLLOW_UP));
    }

    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final AttributionResolver attribution;
    private final AnalyticsService analytics;

    public LeadService(LeadRepository leads, CampaignRepository campaigns,
                       AttributionResolver attribution, AnalyticsService analytics) {
        this.leads = leads;
        this.campaigns = campaigns;
        this.attribution = attribution;
        this.analytics = analytics;
    }

    /** Find-or-create a lead from a WhatsApp identity, applying attribution only on first contact. */
    @Transactional
    public Lead onWhatsAppContact(String waId, String phone, String profileName, Map<String, Object> rawReferral) {
        Lead lead = leads.findByWhatsappWaId(waId).orElse(null);
        boolean created = false;
        if (lead == null) {
            lead = new Lead();
            lead.setWhatsappWaId(waId);
            lead.setWhatsappNumber(phone != null ? phone : waId);
            lead.setName(profileName);
            lead.setFirstContactAt(Instant.now());
            AttributionContext ctx = attribution.resolve(rawReferral);
            applyAttribution(lead, ctx);
            created = true;
        }
        lead.setLastContactAt(Instant.now());
        if (lead.getName() == null && profileName != null) lead.setName(profileName);
        lead = leads.save(lead);

        if (created) {
            log.info("lead created id={} source={} campaign={}", lead.getId(),
                    lead.getAcquisitionSource(), lead.getCampaignId());
            analytics.record(LeadEventType.WHATSAPP_CONVERSATION_STARTED, lead.getId(), null,
                    lead.getAcquisitionSource().name(), lead.getCampaignId(), Map.of("waId", waId));
        }
        analytics.record(LeadEventType.WHATSAPP_MESSAGE_RECEIVED, lead.getId(), null);
        return lead;
    }

    private void applyAttribution(Lead lead, AttributionContext ctx) {
        lead.setAcquisitionSource(ctx.source);
        lead.setAdId(ctx.adId);
        lead.setAdSetId(ctx.adSetId);
        lead.setClickId(ctx.clickId);
        lead.setReferrer(ctx.referrer);
        lead.setLandingPage(ctx.landingPage);
        lead.setUtm(ctx.utm);
        lead.setProviderMetadata(ctx.providerMetadata);
        resolveCampaign(lead, ctx);
    }

    private void resolveCampaign(Lead lead, AttributionContext ctx) {
        Campaign c = null;
        if (ctx.externalCampaignId != null) {
            c = campaigns.findByExternalCampaignId(ctx.externalCampaignId).orElse(null);
        }
        if (c == null && ctx.utm != null && ctx.utm.utmCampaign() != null) {
            c = campaigns.findByUtmUtmCampaignIgnoreCase(ctx.utm.utmCampaign()).orElse(null);
        }
        if (c != null) lead.setCampaignId(c.getId());
    }

    @Transactional
    public Lead updateStatus(UUID leadId, LeadStatus target, boolean enforceTransitions) {
        Lead lead = get(leadId);
        if (enforceTransitions && lead.getStatus() != target
                && !ALLOWED.getOrDefault(lead.getStatus(), Set.of()).contains(target)) {
            throw ApiException.badRequest("ILLEGAL_STATUS_TRANSITION",
                    "Cannot move lead from " + lead.getStatus() + " to " + target);
        }
        lead.setStatus(target);
        if (target == LeadStatus.CONVERTED) {
            analytics.record(LeadEventType.LEAD_CONVERTED, leadId, null);
        }
        return leads.save(lead);
    }

    @Transactional
    public void advanceStatusSoft(UUID leadId, LeadStatus target) {
        Lead lead = get(leadId);
        if (lead.getStatus() == LeadStatus.CONVERTED || lead.getStatus() == LeadStatus.LOST) return;
        // only move forward
        if (target.ordinal() > lead.getStatus().ordinal()) {
            lead.setStatus(target);
            leads.save(lead);
        }
    }

    public Lead get(UUID id) {
        return leads.findById(id).orElseThrow(() -> ApiException.notFound("LEAD_NOT_FOUND", "Lead not found."));
    }

    public List<Lead> all() {
        return leads.findAll();
    }
}
