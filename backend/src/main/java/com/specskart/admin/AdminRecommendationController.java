package com.specskart.admin;

import com.specskart.recommendation.*;
import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ADMIN-only editor for the face-shape -> frame-category rules. */
@RestController
@RequestMapping("/api/admin/recommendations")
public class AdminRecommendationController {

    private final FaceShapeRepository faceShapes;
    private final FrameCategoryRepository frameCategories;
    private final RecommendationRepository recs;

    public AdminRecommendationController(FaceShapeRepository faceShapes, FrameCategoryRepository frameCategories,
                                        RecommendationRepository recs) {
        this.faceShapes = faceShapes;
        this.frameCategories = frameCategories;
        this.recs = recs;
    }

    @GetMapping("/face-shapes")
    public List<FaceShape> faceShapes() { return faceShapes.findAll(); }

    @GetMapping("/frame-categories")
    public List<FrameCategory> frameCategories() { return frameCategories.findAll(); }

    @GetMapping("/rules")
    public List<Map<String, Object>> rules() {
        var shapes = faceShapes.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(FaceShape::getId, s -> s));
        var cats = frameCategories.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(FrameCategory::getId, c -> c));
        return recs.findAll().stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("faceShape", shapes.get(r.getFaceShapeId()) != null ? shapes.get(r.getFaceShapeId()).getCode() : null);
            m.put("frameCategory", cats.get(r.getFrameCategoryId()) != null ? cats.get(r.getFrameCategoryId()).getCode() : null);
            m.put("stance", r.getStance());
            m.put("priority", r.getPriority());
            m.put("reason", r.getRecommendationReason() == null ? "" : r.getRecommendationReason());
            m.put("active", r.isActive());
            return m;
        }).toList();
    }

    public record RuleUpsert(String faceShapeCode, String frameCategoryCode, String stance,
                             Integer priority, String reason, Boolean active) {}

    @PostMapping("/rules")
    public Map<String, Object> upsert(@RequestBody RuleUpsert body) {
        FaceShape shape = faceShapes.findByCodeIgnoreCase(body.faceShapeCode())
                .orElseThrow(() -> ApiException.notFound("FACE_SHAPE_UNKNOWN", "Unknown face shape."));
        FrameCategory cat = frameCategories.findByCodeIgnoreCase(body.frameCategoryCode())
                .orElseThrow(() -> ApiException.notFound("FRAME_CATEGORY_UNKNOWN", "Unknown frame category."));
        FaceShapeFrameRecommendation r = recs.findByFaceShapeIdAndActiveTrueOrderByPriorityAsc(shape.getId()).stream()
                .filter(x -> x.getFrameCategoryId().equals(cat.getId()))
                .findFirst()
                .orElseGet(FaceShapeFrameRecommendation::new);
        r.setFaceShapeId(shape.getId());
        r.setFrameCategoryId(cat.getId());
        if (body.stance() != null) r.setStance(body.stance());
        if (body.priority() != null) r.setPriority(body.priority());
        if (body.reason() != null) r.setRecommendationReason(body.reason());
        if (body.active() != null) r.setActive(body.active());
        recs.save(r);
        return Map.of("ok", true, "id", r.getId());
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        recs.deleteById(id);
        return Map.of("ok", true);
    }
}
