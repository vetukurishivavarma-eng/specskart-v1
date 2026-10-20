package com.specskart.whatsapp;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * POS -> Bookings: the free eye tests customers booked from WhatsApp, last week to next month.
 * Staff tap "Check-up done" when the customer has been in, which stops the "still coming?" and
 * "sorry we missed you" messages for them. Staff and admins alike (it's front-desk work).
 */
@RestController
@RequestMapping("/api/admin/visits")
public class AdminVisitController {

    private final LeadRepository leads;
    private final LeadNudges nudges;

    public AdminVisitController(LeadRepository leads, LeadNudges nudges) {
        this.leads = leads;
        this.nudges = nudges;
    }

    public record VisitView(UUID leadId, String name, String waId, LocalDate visitOn, LocalDate visitedOn, String state) {}

    @GetMapping
    public Map<String, Object> list() {
        LocalDate today = LocalDate.now(LeadNudges.LUSAKA);
        List<VisitView> visits = leads.findByVisitOnBetweenOrderByVisitOnAscCreatedAtAsc(today.minusDays(7), today.plusDays(31))
                .stream().map(l -> view(l, today)).toList();
        return Map.of("today", today, "visits", visits);
    }

    @PostMapping("/{leadId}/done")
    public VisitView done(@PathVariable UUID leadId) {
        nudges.markVisited(leadId, LocalDate.now(LeadNudges.LUSAKA));
        return view(lead(leadId), LocalDate.now(LeadNudges.LUSAKA));
    }

    @PostMapping("/{leadId}/undo")
    public VisitView undo(@PathVariable UUID leadId) {
        nudges.markVisited(leadId, null);
        return view(lead(leadId), LocalDate.now(LeadNudges.LUSAKA));
    }

    private Lead lead(UUID id) {
        return leads.findById(id).orElseThrow(() -> ApiException.notFound("NO_LEAD", "That customer isn't here any more."));
    }

    /** DONE, TODAY, UPCOMING or MISSED -- what the page groups and colours by. */
    static VisitView view(Lead l, LocalDate today) {
        String state = l.getVisitedOn() != null ? "DONE"
                : l.getVisitOn().equals(today) ? "TODAY"
                : l.getVisitOn().isAfter(today) ? "UPCOMING" : "MISSED";
        String waId = l.getWhatsappWaId() != null ? l.getWhatsappWaId() : l.getWhatsappNumber();
        return new VisitView(l.getId(), l.getName(), waId, l.getVisitOn(), l.getVisitedOn(), state);
    }
}
