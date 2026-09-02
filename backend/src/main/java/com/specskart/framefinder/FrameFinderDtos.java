package com.specskart.framefinder;

import com.specskart.faceanalysis.FaceGeometry;
import com.specskart.recommendation.RecommendationDtos;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class FrameFinderDtos {

    public record SessionView(String status, String storeName, String leadFirstName,
                              boolean expired, String consentPolicyVersion) {}

    public record ConsentRequest(@NotNull Boolean cameraConsent, @NotNull Boolean photoProcessingConsent,
                                 String policyVersion) {}

    public record AnalysisRequest(@NotNull FaceGeometry geometry, Boolean retainImage) {}

    public record AnalysisResult(String faceShape, String faceShapeDisplay, double confidence,
                                 String message, List<RecommendationDtos.FrameDto> recommended,
                                 List<RecommendationDtos.FrameDto> avoidOrUseCarefully,
                                 String rulesUsed) {}

    public record EventRequest(@NotNull String event, java.util.Map<String, Object> metadata) {}
}
