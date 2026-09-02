package com.specskart.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<FaceShapeFrameRecommendation, UUID> {
    List<FaceShapeFrameRecommendation> findByFaceShapeIdAndActiveTrueOrderByPriorityAsc(UUID faceShapeId);
}
