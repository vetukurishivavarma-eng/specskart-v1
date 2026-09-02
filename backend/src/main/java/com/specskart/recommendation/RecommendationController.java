package com.specskart.recommendation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/frame-recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/{faceShape}")
    public RecommendationDtos.RecommendationResult get(@PathVariable String faceShape) {
        return service.forFaceShape(faceShape);
    }
}
