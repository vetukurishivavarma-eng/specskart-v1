package com.specskart.admin;

import com.specskart.lead.Lead;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminDtos {

    public record LeadRow(UUID id, String name, String whatsappNumber, String source, UUID campaignId,
                          String campaignName, String faceShape, List<String> recommendedFrames, String status,
                          Instant createdAt, Instant lastContactAt, UUID assignedToUserId, Instant archivedAt) {}

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record TimelineItem(Instant at, String type, String source, Map<String, Object> metadata) {}

    public record NoteDto(UUID id, String body, String authorEmail, Instant createdAt) {}

    public record LeadDetail(LeadRow lead, Map<String, Object> attribution, List<TimelineItem> timeline,
                             List<NoteDto> notes, List<Map<String, Object>> whatsappMessages,
                             List<Map<String, Object>> faceAnalyses, List<Map<String, Object>> consents) {}

    public record StatusUpdate(String status) {}
    public record NewNote(String body) {}
    public record Assign(UUID userId) {}

    public record CampaignDto(UUID id, String name, String platform, String externalCampaignId,
                              String status, Double budget, String destination,
                              Map<String, String> utm, Map<String, Object> metadata,
                              String startDate, String endDate) {}

    public record NewCampaign(String name, String platform, String externalCampaignId, Double budget,
                              String destination, String utmSource, String utmMedium, String utmCampaign,
                              String utmContent, String utmTerm, String startDate, String endDate,
                              Map<String, Object> metadata) {}

    public record DashboardStats(long totalLeads, long todayLeads, long faceAnalysesCompleted,
                                 double analysisConversionPct, long whatsappConversations, long hotLeads,
                                 long newLeads, long followUpsDue, Map<String, Long> leadsBySource,
                                 Map<String, Long> leadsByStatus) {}

    public record CampaignPerformance(UUID campaignId, String name, String platform, long leads,
                                      long faceAnalyses, double analysisConversionPct, long qualified,
                                      long converted, Double budget, Double costPerLead) {}
}
