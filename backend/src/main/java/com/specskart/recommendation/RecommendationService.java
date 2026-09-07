package com.specskart.recommendation;

import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Transparent additive re-ranking: a matching vibe shaves BOOST off a category's priority
    // (lower priority = higher rank). No ML, fully deterministic.
    private static final int BOOST = 25;
    private static final Map<String, Set<String>> VIBE_FAVOURS = Map.of(
            "classic", Set.of("WAYFARER", "OVAL_FRAME", "ROUND_FRAME"),
            "bold", Set.of("GEOMETRIC", "CATEYE", "BROWLINE", "OVERSIZED"),
            "minimal", Set.of("THIN_RIM", "OVAL_FRAME"));

    /** Face-shape recommendations re-weighted by an optional style profile. */
    @Transactional(readOnly = true)
    public RecommendationDtos.RecommendationResult forFaceShapeWithStyle(String faceShapeCode,
                                                                        RecommendationDtos.StyleProfile style) {
        RecommendationDtos.RecommendationResult base = forFaceShape(faceShapeCode);
        if (style == null || style.isEmpty()) return base;

        Set<String> favoured = style.vibe() == null ? Set.of()
                : VIBE_FAVOURS.getOrDefault(style.vibe().toLowerCase(), Set.of());
        // ponytail: colour is only a tie-break here — it needs per-product colour data to matter more.
        int colourNudge = style.colour() == null ? 0 : 1;

        List<RecommendationDtos.FrameDto> ranked = base.recommended().stream()
                .map(f -> new RecommendationDtos.FrameDto(f.code(), f.displayName(), f.description(), f.imageUrl(),
                        f.priority() - (favoured.contains(f.code().toUpperCase()) ? BOOST : 0) - colourNudge,
                        f.reason()))
                .sorted(Comparator.comparingInt(RecommendationDtos.FrameDto::priority))
                .toList();

        return new RecommendationDtos.RecommendationResult(base.faceShape(), base.faceShapeDisplay(),
                base.faceShapeDescription(), ranked, base.avoidOrUseCarefully());
    }
}
