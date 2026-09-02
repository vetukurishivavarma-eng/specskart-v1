package com.specskart.admin;

import com.specskart.campaign.*;
import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/campaigns")
public class AdminCampaignController {

    private final CampaignRepository campaigns;

    public AdminCampaignController(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping
    public List<AdminDtos.CampaignDto> list() {
        return campaigns.findAll().stream().map(AdminMapper::campaign).toList();
    }

    @GetMapping("/{id}")
    public AdminDtos.CampaignDto get(@PathVariable UUID id) {
        return campaigns.findById(id).map(AdminMapper::campaign)
                .orElseThrow(() -> ApiException.notFound("CAMPAIGN_NOT_FOUND", "Campaign not found."));
    }

    @PostMapping
    public AdminDtos.CampaignDto create(@RequestBody AdminDtos.NewCampaign body) {
        if (body.name() == null || body.name().isBlank()) {
            throw ApiException.badRequest("CAMPAIGN_NAME_REQUIRED", "Campaign name is required.");
        }
        Campaign c = new Campaign();
        c.setName(body.name());
        c.setPlatform(parsePlatform(body.platform()));
        c.setExternalCampaignId(body.externalCampaignId());
        c.setBudget(body.budget());
        c.setDestination(body.destination());
        c.setUtm(new UtmData(body.utmSource(), body.utmMedium(), body.utmCampaign(),
                body.utmContent(), body.utmTerm()));
        c.setStatus(CampaignStatus.ACTIVE);
        if (body.startDate() != null && !body.startDate().isBlank()) c.setStartDate(LocalDate.parse(body.startDate()));
        if (body.endDate() != null && !body.endDate().isBlank()) c.setEndDate(LocalDate.parse(body.endDate()));
        c.setMetadata(body.metadata() != null ? body.metadata() : new HashMap<>());
        return AdminMapper.campaign(campaigns.save(c));
    }

    @PatchMapping("/{id}/status")
    public AdminDtos.CampaignDto status(@PathVariable UUID id, @RequestBody AdminDtos.StatusUpdate body) {
        Campaign c = campaigns.findById(id)
                .orElseThrow(() -> ApiException.notFound("CAMPAIGN_NOT_FOUND", "Campaign not found."));
        c.setStatus(CampaignStatus.valueOf(body.status()));
        return AdminMapper.campaign(campaigns.save(c));
    }

    private static Platform parsePlatform(String p) {
        try {
            return Platform.valueOf(p == null ? "OTHER" : p.toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_PLATFORM", "Unknown platform: " + p);
        }
    }
}
