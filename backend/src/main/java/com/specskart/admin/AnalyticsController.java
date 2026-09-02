package com.specskart.admin;

import com.specskart.analytics.LeadEventRepository;
import com.specskart.analytics.LeadEventType;
import com.specskart.campaign.Campaign;
import com.specskart.campaign.CampaignRepository;
import com.specskart.faceanalysis.FaceAnalysisRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private final LeadRepository leads;
    private final LeadEventRepository events;
    private final FaceAnalysisRepository faceAnalyses;
    private final CampaignRepository campaigns;

    public AnalyticsController(LeadRepository leads, LeadEventRepository events,
                              FaceAnalysisRepository faceAnalyses, CampaignRepository campaigns) {
        this.leads = leads;
        this.events = events;
        this.faceAnalyses = faceAnalyses;
        this.campaigns = campaigns;
    }

    @GetMapping("/dashboard")
    public AdminDtos.DashboardStats dashboard() {
        List<Lead> all = leads.findAll();
        long total = all.size();
        var startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long today = all.stream().filter(l -> l.getCreatedAt().isAfter(startOfToday)).count();
        long analysesDone = faceAnalyses.count();
        long waConversations = events.countByEventType(LeadEventType.WHATSAPP_CONVERSATION_STARTED);
        double conv = total == 0 ? 0 : Math.round(analysesDone * 1000.0 / total) / 10.0;

        long hot = all.stream().filter(l -> l.getStatus() == LeadStatus.INTERESTED
                || l.getStatus() == LeadStatus.FACE_ANALYSIS_COMPLETED).count();
        long fresh = all.stream().filter(l -> l.getStatus() == LeadStatus.NEW).count();
        long followUps = all.stream().filter(l -> l.getStatus() == LeadStatus.FOLLOW_UP).count();

        Map<String, Long> bySource = all.stream().collect(Collectors.groupingBy(
                l -> l.getAcquisitionSource().name(), TreeMap::new, Collectors.counting()));
        Map<String, Long> byStatus = all.stream().collect(Collectors.groupingBy(
                l -> l.getStatus().name(), TreeMap::new, Collectors.counting()));

        return new AdminDtos.DashboardStats(total, today, analysesDone, conv, waConversations,
                hot, fresh, followUps, bySource, byStatus);
    }

    @GetMapping("/campaigns")
    public List<AdminDtos.CampaignPerformance> campaignPerformance() {
        List<Lead> all = leads.findAll();
        return campaigns.findAll().stream().map(c -> {
            List<Lead> cl = all.stream().filter(l -> c.getId().equals(l.getCampaignId())).toList();
            long n = cl.size();
            long fa = cl.stream().filter(l -> l.getFaceShape() != null).count();
            long qualified = cl.stream().filter(l -> l.getStatus().ordinal()
                    >= LeadStatus.FACE_ANALYSIS_COMPLETED.ordinal() && l.getStatus() != LeadStatus.LOST).count();
            long converted = cl.stream().filter(l -> l.getStatus() == LeadStatus.CONVERTED).count();
            double convPct = n == 0 ? 0 : Math.round(fa * 1000.0 / n) / 10.0;
            Double cpl = (c.getBudget() != null && n > 0) ? Math.round(c.getBudget() / n * 100.0) / 100.0 : null;
            return new AdminDtos.CampaignPerformance(c.getId(), c.getName(), c.getPlatform().name(),
                    n, fa, convPct, qualified, converted, c.getBudget(), cpl);
        }).toList();
    }
}
