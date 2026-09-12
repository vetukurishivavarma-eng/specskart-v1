package com.specskart.lens;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Public API for the lens-only inquiry funnel. The inquiry id itself is the bearer
 *  capability for the rest of the flow — same unguessable-id convention as order tracking
 *  elsewhere on this site. */
@RestController
@RequestMapping("/api/public/lens")
public class LensController {

    private final LensInquiryService service;

    public LensController(LensInquiryService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public LensDtos.StartResult start(@RequestBody LensDtos.StartVerification body) {
        UUID id = service.start(body.phone(), body.lensType(), Boolean.TRUE.equals(body.blueBlock()));
        return new LensDtos.StartResult(id);
    }

    @PostMapping("/verify/{token}")
    public Map<String, Object> verify(@PathVariable String token) {
        return Map.of("verified", service.verify(token));
    }

    @GetMapping("/{id}")
    public LensDtos.InquiryView status(@PathVariable UUID id) {
        return service.status(id);
    }

    @PatchMapping("/{id}")
    public LensDtos.InquiryView update(@PathVariable UUID id, @RequestBody LensDtos.UpdateDetails body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/quote")
    public LensDtos.InquiryView quote(@PathVariable UUID id) {
        return service.quote(id);
    }

    @PostMapping("/{id}/submit")
    public LensDtos.InquiryView submit(@PathVariable UUID id) {
        return service.submit(id);
    }
}
