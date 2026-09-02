package com.specskart.recommendation;

import java.util.List;

public class RecommendationDtos {
    public record FrameDto(String code, String displayName, String description, String imageUrl,
                           int priority, String reason) {}
    public record RecommendationResult(String faceShape, String faceShapeDisplay, String faceShapeDescription,
                                       List<FrameDto> recommended, List<FrameDto> avoidOrUseCarefully) {}
}
