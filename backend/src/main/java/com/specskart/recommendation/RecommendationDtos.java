package com.specskart.recommendation;

import java.util.List;

public class RecommendationDtos {
    public record FrameDto(String code, String displayName, String description, String imageUrl,
                           int priority, String reason) {}

    /** Canonical style-quiz answers; any field may be null. */
    public record StyleProfile(String vibe, String colour, String budget, String screenHours) {
        public boolean isEmpty() {
            return vibe == null && colour == null && budget == null && screenHours == null;
        }
    }
    public record RecommendationResult(String faceShape, String faceShapeDisplay, String faceShapeDescription,
                                       List<FrameDto> recommended, List<FrameDto> avoidOrUseCarefully) {}
}
