package com.specskart.framefinder;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import com.specskart.shared.ApiException;
import com.specskart.shared.ConsentRecord;
import com.specskart.shared.ConsentRecordRepository;
import com.specskart.shared.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class FrameFinderService {

    private static final Logger log = LoggerFactory.getLogger(FrameFinderService.class);
    public static final String POLICY_VERSION = "2026-09-01";

    private final FrameFinderSessionRepository sessions;
    private final ConsentRecordRepository consents;
    private final TokenGenerator tokens;
    private final AppProperties props;
    private final LeadService leadService;
    private final AnalyticsService analytics;

    public FrameFinderService(FrameFinderSessionRepository sessions, ConsentRecordRepository consents,
                              TokenGenerator tokens, AppProperties props, LeadService leadService,
                              AnalyticsService analytics) {
        this.sessions = sessions;
        this.consents = consents;
        this.tokens = tokens;
        this.props = props;
        this.leadService = leadService;
        this.analytics = analytics;
    }

    public record CreatedSession(FrameFinderSession session, String url) {}

    /** Creates a fresh opaque session and returns the personalized URL (no phone number in it). */
    @Transactional
    public CreatedSession createForLead(UUID leadId, UUID campaignId) {
        Lead lead = leadService.get(leadId);
        String raw = tokens.newToken();
        FrameFinderSession s = new FrameFinderSession();
        s.setLeadId(lead.getId());
        s.setTokenHash(tokens.hash(raw));
        s.setExpiresAt(Instant.now().plus(props.session().expiryHours(), ChronoUnit.HOURS));
        s.setSourceCampaignId(campaignId != null ? campaignId : lead.getCampaignId());
        s = sessions.save(s);
        analytics.record(LeadEventType.FRAME_FINDER_LINK_SENT, lead.getId(), s.getId().toString());
        String url = props.frontendBaseUrl() + "/frame-finder?s=" + raw;
        log.info("frame-finder session {} created for lead {}", s.getId(), lead.getId());
        return new CreatedSession(s, url);
    }

    @Transactional
    public FrameFinderSession resolve(String rawToken) {
        FrameFinderSession s = sessions.findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(() -> ApiException.notFound("FRAME_SESSION_NOT_FOUND",
                        "This Frame Finder link is not valid."));
        if (s.isExpired()) {
            s.setStatus(FrameFinderSessionStatus.EXPIRED);
            sessions.save(s);
            throw ApiException.gone("FRAME_SESSION_EXPIRED", "This Frame Finder link has expired.");
        }
        s.setLastAccessedAt(Instant.now());
        if (s.getStatus() == FrameFinderSessionStatus.CREATED) {
            s.setStatus(FrameFinderSessionStatus.OPENED);
            analytics.record(LeadEventType.FRAME_FINDER_OPENED, s.getLeadId(), s.getId().toString());
            leadService.advanceStatusSoft(s.getLeadId(), com.specskart.lead.LeadStatus.ENGAGED);
        }
        return sessions.save(s);
    }

    @Transactional
    public void recordConsent(FrameFinderSession s, boolean camera, boolean photoProcessing, String policyVersion) {
        String pv = policyVersion != null ? policyVersion : POLICY_VERSION;
        save(s.getLeadId(), "CAMERA", camera ? "GRANTED" : "DENIED", pv);
        save(s.getLeadId(), "PHOTO_PROCESSING", photoProcessing ? "GRANTED" : "DENIED", pv);
        analytics.record(camera ? LeadEventType.CAMERA_PERMISSION_GRANTED : LeadEventType.CAMERA_PERMISSION_DENIED,
                s.getLeadId(), s.getId().toString());
    }

    private void save(UUID leadId, String type, String status, String pv) {
        ConsentRecord c = new ConsentRecord();
        c.setLeadId(leadId);
        c.setType(type);
        c.setStatus(status);
        c.setSource("FRAME_FINDER");
        c.setPolicyVersion(pv);
        consents.save(c);
    }

    public boolean hasPhotoConsent(UUID leadId) {
        return consents.existsByLeadIdAndTypeAndStatus(leadId, "PHOTO_PROCESSING", "GRANTED");
    }

    @Transactional
    public void markStatus(FrameFinderSession s, FrameFinderSessionStatus status) {
        s.setStatus(status);
        if (status == FrameFinderSessionStatus.COMPLETED) s.setCompletedAt(Instant.now());
        sessions.save(s);
    }
}
