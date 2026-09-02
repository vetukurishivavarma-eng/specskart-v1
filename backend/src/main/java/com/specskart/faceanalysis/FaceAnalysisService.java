package com.specskart.faceanalysis;

import com.specskart.analytics.AnalyticsService;
import com.specskart.analytics.LeadEventType;
import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import com.specskart.recommendation.RecommendationDtos;
import com.specskart.recommendation.RecommendationService;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FaceAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FaceAnalysisService.class);

    private final FaceShapeClassifier classifier;
    private final FaceAnalysisRepository repo;
    private final RecommendationService recommendations;
    private final LeadRepository leads;
    private final AnalyticsService analytics;
    private final AppProperties props;

    public FaceAnalysisService(FaceShapeClassifier classifier, FaceAnalysisRepository repo,
                               RecommendationService recommendations, LeadRepository leads,
                               AnalyticsService analytics, AppProperties props) {
        this.classifier = classifier;
        this.repo = repo;
        this.recommendations = recommendations;
        this.leads = leads;
        this.analytics = analytics;
        this.props = props;
    }

    public record Outcome(FaceAnalysis analysis, FaceShapeClassifier.Result classification,
                          RecommendationDtos.RecommendationResult recommendation) {}

    @Transactional
    public Outcome analyse(UUID leadId, UUID sessionId, FaceGeometry geometry, boolean photoConsent,
                           boolean retainImageRequested) {
        if (!photoConsent) {
            throw ApiException.badRequest("PHOTO_CONSENT_REQUIRED",
                    "Photo-processing consent is required before analysis.");
        }
        validate(geometry);
        analytics.record(LeadEventType.FACE_ANALYSIS_STARTED, leadId, str(sessionId));

        FaceShapeClassifier.Result result;
        try {
            result = classifier.classify(geometry);
        } catch (RuntimeException e) {
            analytics.record(LeadEventType.FACE_ANALYSIS_FAILED, leadId, str(sessionId));
            throw ApiException.badRequest("FACE_ANALYSIS_FAILED", "We could not analyze this photo. Please try another.");
        }

        RecommendationDtos.RecommendationResult rec = recommendations.forFaceShape(result.faceShape());
        List<String> recCodes = rec.recommended().stream().map(RecommendationDtos.FrameDto::displayName).toList();

        FaceAnalysis fa = new FaceAnalysis();
        fa.setLeadId(leadId);
        fa.setSessionId(sessionId);
        fa.setPredictedFaceShape(result.faceShape());
        fa.setConfidenceScore(result.confidence());
        fa.setRecommendationSummary("We think your face is closest to " + rec.faceShapeDisplay()
                + ". Recommended styles: " + String.join(", ", recCodes) + ".");
        fa.setRecommendedFrameCategories(recCodes);
        fa.setGeometryData(Map.of(
                "faceWidthRatio", geometry.faceWidthRatio(),
                "foreheadWidthRatio", geometry.foreheadWidthRatio(),
                "cheekboneWidthRatio", geometry.cheekboneWidthRatio(),
                "jawWidthRatio", geometry.jawWidthRatio(),
                "jawAngleDeg", geometry.jawAngleDeg(),
                "chinRatio", geometry.chinRatio(),
                "scores", result.scores(),
                "rulesUsed", result.rulesUsed()));
        // Privacy: image is never sent to the server. Retention flag recorded for auditing only.
        fa.setConsentGiven(true);
        fa.setImageStorageReference(props.face().retainImages() && retainImageRequested ? "retention-opted-in" : null);
        fa = repo.save(fa);

        Lead lead = leads.findById(leadId).orElseThrow();
        lead.setFaceShape(result.faceShape());
        lead.setFaceConfidence(result.confidence());
        lead.setRecommendedFrameCategories(recCodes);
        if (lead.getStatus().ordinal() < LeadStatus.FACE_ANALYSIS_COMPLETED.ordinal()
                && lead.getStatus() != LeadStatus.LOST) {
            lead.setStatus(LeadStatus.FACE_ANALYSIS_COMPLETED);
        }
        leads.save(lead);

        analytics.record(LeadEventType.FACE_ANALYSIS_COMPLETED, leadId, str(sessionId), null, lead.getCampaignId(),
                Map.of("faceShape", result.faceShape(), "confidence", result.confidence()));
        log.info("face analysis lead={} shape={} confidence={}", leadId, result.faceShape(), result.confidence());
        return new Outcome(fa, result, rec);
    }

    private void validate(FaceGeometry g) {
        double[] ratios = {g.faceWidthRatio(), g.foreheadWidthRatio(), g.cheekboneWidthRatio(),
                g.jawWidthRatio(), g.chinRatio()};
        for (double r : ratios) {
            if (r <= 0 || r > 2.0 || Double.isNaN(r)) {
                throw ApiException.badRequest("FACE_GEOMETRY_INVALID",
                        "The photo did not produce a usable face measurement. Please try again facing the camera directly.");
            }
        }
        if (g.jawAngleDeg() < 60 || g.jawAngleDeg() > 200) {
            throw ApiException.badRequest("FACE_GEOMETRY_INVALID",
                    "Please try facing the camera directly with your whole face visible.");
        }
    }

    public java.util.Optional<FaceAnalysis> latestForSession(UUID sessionId) {
        return repo.findFirstBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    public List<FaceAnalysis> forLead(UUID leadId) {
        return repo.findByLeadIdOrderByCreatedAtDesc(leadId);
    }

    private static String str(UUID u) { return u == null ? null : u.toString(); }
}
