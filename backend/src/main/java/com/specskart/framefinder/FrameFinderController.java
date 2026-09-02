package com.specskart.framefinder;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.config.AppProperties;
import com.specskart.faceanalysis.FaceAnalysis;
import com.specskart.faceanalysis.FaceAnalysisService;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import com.specskart.shared.ApiException;
import com.specskart.whatsapp.WhatsAppBotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/frame-finder/session/{token}")
public class FrameFinderController {

    private final FrameFinderService sessions;
    private final FaceAnalysisService faceAnalysis;
    private final LeadService leadService;
    private final WhatsAppBotService bot;
    private final AnalyticsService analytics;
    private final AppProperties props;

    public FrameFinderController(FrameFinderService sessions, FaceAnalysisService faceAnalysis,
                                 LeadService leadService, WhatsAppBotService bot,
                                 AnalyticsService analytics, AppProperties props) {
        this.sessions = sessions;
        this.faceAnalysis = faceAnalysis;
        this.leadService = leadService;
        this.bot = bot;
        this.analytics = analytics;
        this.props = props;
    }

    @GetMapping
    public FrameFinderDtos.SessionView get(@PathVariable String token) {
        FrameFinderSession s = sessions.resolve(token);
        Lead lead = leadService.get(s.getLeadId());
        String firstName = lead.getName() == null ? null : lead.getName().split(" ")[0];
        return new FrameFinderDtos.SessionView(s.getStatus().name(), props.storeName(), firstName,
                s.isExpired(), FrameFinderService.POLICY_VERSION);
    }

    @PostMapping("/consent")
    public FrameFinderDtos.SessionView consent(@PathVariable String token,
                                               @Valid @RequestBody FrameFinderDtos.ConsentRequest req) {
        FrameFinderSession s = sessions.resolve(token);
        if (!req.photoProcessingConsent()) {
            throw ApiException.badRequest("PHOTO_CONSENT_REQUIRED",
                    "Photo-processing consent is required to continue.");
        }
        sessions.recordConsent(s, req.cameraConsent(), req.photoProcessingConsent(), req.policyVersion());
        sessions.markStatus(s, FrameFinderSessionStatus.ANALYSIS_STARTED);
        return get(token);
    }

    @PostMapping("/event")
    public void event(@PathVariable String token, @Valid @RequestBody FrameFinderDtos.EventRequest req) {
        FrameFinderSession s = sessions.resolve(token);
        LeadEventType type;
        try {
            type = LeadEventType.valueOf(req.event());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("UNKNOWN_EVENT", "Unknown event: " + req.event());
        }
        analytics.record(type, s.getLeadId(), s.getId().toString(), "frame-finder", null, req.metadata());
    }

    @PostMapping("/analysis")
    public FrameFinderDtos.AnalysisResult analyse(@PathVariable String token,
                                                  @Valid @RequestBody FrameFinderDtos.AnalysisRequest req) {
        FrameFinderSession s = sessions.resolve(token);
        if (!sessions.hasPhotoConsent(s.getLeadId())) {
            throw ApiException.badRequest("PHOTO_CONSENT_REQUIRED",
                    "Photo-processing consent is required before analysis.");
        }
        var outcome = faceAnalysis.analyse(s.getLeadId(), s.getId(), req.geometry(), true,
                Boolean.TRUE.equals(req.retainImage()));
        sessions.markStatus(s, FrameFinderSessionStatus.COMPLETED);

        Lead lead = leadService.get(s.getLeadId());
        bot.sendAnalysisFollowUp(lead, outcome.recommendation().faceShapeDisplay(),
                outcome.analysis().getRecommendedFrameCategories());

        var rec = outcome.recommendation();
        return new FrameFinderDtos.AnalysisResult(
                rec.faceShape(), rec.faceShapeDisplay(), outcome.classification().confidence(),
                "We think your face is closest to " + rec.faceShapeDisplay() + ".",
                rec.recommended(), rec.avoidOrUseCarefully(), outcome.classification().rulesUsed());
    }

    @PostMapping("/send-to-whatsapp")
    public void sendToWhatsApp(@PathVariable String token) {
        FrameFinderSession s = sessions.resolve(token);
        FaceAnalysis fa = faceAnalysis.latestForSession(s.getId())
                .orElseThrow(() -> ApiException.badRequest("NO_ANALYSIS", "No analysis found for this session yet."));
        Lead lead = leadService.get(s.getLeadId());
        bot.sendAnalysisFollowUp(lead, fa.getPredictedFaceShape(), fa.getRecommendedFrameCategories());
        analytics.record(LeadEventType.WHATSAPP_RESULTS_REQUESTED, lead.getId(), s.getId().toString());
    }
}
