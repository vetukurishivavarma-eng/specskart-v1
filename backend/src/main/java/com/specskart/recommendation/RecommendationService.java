package com.specskart.recommendation;

import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final FaceShapeRepository faceShapes;
    private final FrameCategoryRepository frameCategories;
    private final RecommendationRepository recs;

    public RecommendationService(FaceShapeRepository faceShapes, FrameCategoryRepository frameCategories,
                                 RecommendationRepository recs) {
        this.faceShapes = faceShapes;
        this.frameCategories = frameCategories;
        this.recs = recs;
    }

    @Transactional(readOnly = true)
    public RecommendationDtos.RecommendationResult forFaceShape(String faceShapeCode) {
        FaceShape shape = faceShapes.findByCodeIgnoreCase(faceShapeCode)
                .orElseThrow(() -> ApiException.notFound("FACE_SHAPE_UNKNOWN",
                        "No recommendation rules for face shape: " + faceShapeCode));

        Map<UUID, FrameCategory> byId = frameCategories.findAll().stream()
                .collect(Collectors.toMap(FrameCategory::getId, c -> c));

        List<RecommendationDtos.FrameDto> pos = new ArrayList<>();
        List<RecommendationDtos.FrameDto> caution = new ArrayList<>();
        for (FaceShapeFrameRecommendation r : recs.findByFaceShapeIdAndActiveTrueOrderByPriorityAsc(shape.getId())) {
            FrameCategory c = byId.get(r.getFrameCategoryId());
            if (c == null || !c.isActive()) continue;
            var dto = new RecommendationDtos.FrameDto(c.getCode(), c.getDisplayName(), c.getDescription(),
                    c.getImageUrl(), r.getPriority(), r.getRecommendationReason());
            if ("CAUTION".equalsIgnoreCase(r.getStance())) caution.add(dto);
            else pos.add(dto);
        }
        return new RecommendationDtos.RecommendationResult(shape.getCode(), shape.getDisplayName(),
                shape.getDescription(), pos, caution);
    }

    public List<String> recommendedCodes(String faceShapeCode) {
        return forFaceShape(faceShapeCode).recommended().stream()
                .map(RecommendationDtos.FrameDto::displayName).toList();
    }
}
