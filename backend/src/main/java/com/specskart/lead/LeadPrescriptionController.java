package com.specskart.lead;

import com.specskart.order.PrescriptionFile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A customer's prescriptions on their lead screen. AGENT + ADMIN; DELETE is ADMIN-only (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class LeadPrescriptionController {

    private final LeadPrescriptionService prescriptions;
    private final LeadService leads;

    public LeadPrescriptionController(LeadPrescriptionService prescriptions, LeadService leads) {
        this.prescriptions = prescriptions;
        this.leads = leads;
    }

    /** The counter bill: last prescriptions for the number being typed. */
    @GetMapping("/prescriptions")
    public List<Map<String, Object>> byPhone(@RequestParam String phone) {
        return prescriptions.forPhone(phone);
    }

    @GetMapping("/leads/{leadId}/prescriptions")
    public List<Map<String, Object>> list(@PathVariable UUID leadId) {
        return prescriptions.forLead(leads.get(leadId));
    }

    @PutMapping("/leads/{leadId}/prescriptions/{id}")
    public Map<String, Object> setNumbers(@PathVariable UUID leadId, @PathVariable UUID id,
                                          @RequestBody LeadPrescriptionService.Numbers body) {
        prescriptions.setNumbers(leadId, id, body);
        return Map.of("saved", id);
    }

    @GetMapping("/leads/{leadId}/prescriptions/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID leadId, @PathVariable UUID id) {
        PrescriptionFile f = prescriptions.photo(leadId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                // a unique name: the app caches downloads by filename
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"rx-" + id + "."
                        + f.getContentType().substring(f.getContentType().indexOf('/') + 1) + "\"")
                .body(f.getBytes());
    }

    @DeleteMapping("/leads/{leadId}/prescriptions/{id}")
    public Map<String, Object> delete(@PathVariable UUID leadId, @PathVariable UUID id) {
        prescriptions.delete(leadId, id);
        return Map.of("deleted", id);
    }
}
