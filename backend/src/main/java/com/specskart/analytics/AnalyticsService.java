package com.specskart.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private final LeadEventRepository events;

    public AnalyticsService(LeadEventRepository events) {
        this.events = events;
    }

    public void record(LeadEventType type, UUID leadId, String sessionId, String source,
                       UUID campaignId, Map<String, Object> metadata) {
        LeadEvent e = new LeadEvent();
        e.setEventType(type);
        e.setLeadId(leadId);
        e.setSessionId(sessionId);
        e.setSource(source);
        e.setCampaignId(campaignId);
        if (metadata != null) e.setMetadata(metadata);
        events.save(e);
        log.debug("event {} lead={} session={}", type, leadId, sessionId);
    }

    public void record(LeadEventType type, UUID leadId, String sessionId) {
        record(type, leadId, sessionId, null, null, null);
    }
}
