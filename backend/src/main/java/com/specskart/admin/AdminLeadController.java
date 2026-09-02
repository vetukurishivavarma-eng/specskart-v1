package com.specskart.admin;

import com.specskart.analytics.LeadEvent;
import com.specskart.analytics.LeadEventRepository;
import com.specskart.campaign.Campaign;
import com.specskart.campaign.CampaignRepository;
import com.specskart.faceanalysis.FaceAnalysis;
import com.specskart.faceanalysis.FaceAnalysisService;
import com.specskart.lead.*;
import com.specskart.shared.ApiException;
import com.specskart.shared.ConsentRecord;
import com.specskart.shared.ConsentRecordRepository;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/leads")
public class AdminLeadController {

    private final LeadRepository leads;
    private final LeadService leadService;
    private final LeadNoteRepository notes;
    private final CampaignRepository campaigns;
    private final LeadEventRepository events;
    private final WhatsAppMessageRepository waMessages;
    private final FaceAnalysisService faceAnalysis;
    private final ConsentRecordRepository consents;

    public AdminLeadController(LeadRepository leads, LeadService leadService, LeadNoteRepository notes,
                              CampaignRepository campaigns, LeadEventRepository events,
                              WhatsAppMessageRepository waMessages, FaceAnalysisService faceAnalysis,
                              ConsentRecordRepository consents) {
        this.leads = leads;
        this.leadService = leadService;
        this.notes = notes;
        this.campaigns = campaigns;
        this.events = events;
        this.waMessages = waMessages;
        this.faceAnalysis = faceAnalysis;
        this.consents = consents;
    }

    @GetMapping
    public AdminDtos.Page<AdminDtos.LeadRow> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) UUID campaignId,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        LeadStatus ls = status == null || status.isBlank() ? null : LeadStatus.valueOf(status);
        var pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = leads.search(ls, campaignId, q == null || q.isBlank() ? null : q, pr);
        Map<UUID, String> names = campaignNames();
        var rows = result.map(l -> AdminMapper.row(l, names.get(l.getCampaignId()))).getContent();
        return new AdminDtos.Page<>(rows, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public AdminDtos.LeadDetail detail(@PathVariable UUID id) {
        Lead l = leadService.get(id);
        String campaignName = l.getCampaignId() == null ? null :
                campaigns.findById(l.getCampaignId()).map(Campaign::getName).orElse(null);

        List<AdminDtos.TimelineItem> timeline = events.findByLeadIdOrderByCreatedAtAsc(id).stream()
                .map(e -> new AdminDtos.TimelineItem(e.getCreatedAt(), e.getEventType().name(),
                        e.getSource(), e.getMetadata()))
                .toList();

        List<AdminDtos.NoteDto> noteDtos = notes.findByLeadIdOrderByCreatedAtDesc(id).stream()
                .map(n -> new AdminDtos.NoteDto(n.getId(), n.getBody(), n.getAuthorEmail(), n.getCreatedAt()))
                .toList();

        List<Map<String, Object>> msgs = waMessages.findByLeadIdOrderByCreatedAtAsc(id).stream()
                .map(m -> mapOf("at", m.getCreatedAt(), "direction", m.getDirection(),
                        "type", m.getMessageType(), "body", m.getBody(), "status", m.getStatus()))
                .toList();

        List<Map<String, Object>> analyses = faceAnalysis.forLead(id).stream()
                .map(a -> mapOf("at", a.getCreatedAt(), "faceShape", a.getPredictedFaceShape(),
                        "confidence", a.getConfidenceScore(), "recommended", a.getRecommendedFrameCategories(),
                        "summary", a.getRecommendationSummary(), "geometry", a.getGeometryData(),
                        "analysisVersion", a.getAnalysisVersion(), "imageRetained", a.getImageStorageReference() != null))
                .toList();

        List<Map<String, Object>> consentDtos = consents.findByLeadIdOrderByCreatedAtDesc(id).stream()
                .map(c -> mapOf("at", c.getCreatedAt(), "type", c.getType(), "status", c.getStatus(),
                        "source", c.getSource(), "policyVersion", c.getPolicyVersion()))
                .toList();

        return new AdminDtos.LeadDetail(AdminMapper.row(l, campaignName), AdminMapper.attribution(l),
                timeline, noteDtos, msgs, analyses, consentDtos);
    }

    @PatchMapping("/{id}/status")
    public AdminDtos.LeadRow updateStatus(@PathVariable UUID id, @RequestBody AdminDtos.StatusUpdate body) {
        LeadStatus target;
        try {
            target = LeadStatus.valueOf(body.status());
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_STATUS", "Unknown status: " + body.status());
        }
        Lead l = leadService.updateStatus(id, target, true);
        return AdminMapper.row(l, campaignNames().get(l.getCampaignId()));
    }

    @PostMapping("/{id}/notes")
    public AdminDtos.NoteDto addNote(@PathVariable UUID id, @RequestBody AdminDtos.NewNote body, Authentication auth) {
        leadService.get(id);
        if (body.body() == null || body.body().isBlank()) {
            throw ApiException.badRequest("EMPTY_NOTE", "Note body is required.");
        }
        LeadNote n = new LeadNote();
        n.setLeadId(id);
        n.setBody(body.body());
        n.setAuthorEmail(auth != null ? String.valueOf(auth.getPrincipal()) : "system");
        n = notes.save(n);
        return new AdminDtos.NoteDto(n.getId(), n.getBody(), n.getAuthorEmail(), n.getCreatedAt());
    }

    @PostMapping("/{id}/assign")
    public AdminDtos.LeadRow assign(@PathVariable UUID id, @RequestBody AdminDtos.Assign body) {
        Lead l = leadService.get(id);
        l.setAssignedToUserId(body.userId());
        leads.save(l);
        return AdminMapper.row(l, campaignNames().get(l.getCampaignId()));
    }

    private Map<UUID, String> campaignNames() {
        Map<UUID, String> m = new LinkedHashMap<>();
        campaigns.findAll().forEach(c -> m.put(c.getId(), c.getName()));
        return m;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
