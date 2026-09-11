package com.specskart.lead;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deterministic lead-temperature score — "how likely to buy right now" — from
 * signals already on the lead. No ML model, no LLM call: just weighted rules,
 * computed inline wherever a lead is read (cheap, no extra queries), so it's
 * safe to show on every row of the admin list as well as the detail page.
 * Drives dynamic-offer decisions in {@link LeadFollowUpService} and lets staff
 * triage who to call first.
 */
public final class LeadScoring {

    private LeadScoring() {}

    public enum Temperature { HOT, WARM, COLD }

    public record Score(int points, Temperature temperature) {}

    public static Score of(Lead lead) {
        if (lead.getStatus() == LeadStatus.CONVERTED) return new Score(100, Temperature.HOT);
        if (lead.getStatus() == LeadStatus.LOST || lead.getFollowUpState() == FollowUpState.OPTED_OUT) {
            return new Score(0, Temperature.COLD);
        }

        int pts = switch (lead.getStatus()) {
            case FACE_ANALYSIS_COMPLETED, INTERESTED, FOLLOW_UP -> 35;
            case ENGAGED, FACE_ANALYSIS_STARTED -> 15;
            default -> 0;
        };
        if (lead.getFaceShape() != null) pts += 15;      // completed Frame Finder
        if (lead.getStyleVibe() != null) pts += 15;       // took the style quiz
        if (lead.getPoints() > 0) pts += 10;              // has loyalty history
        if (lead.getReferralCode() != null) pts += 5;     // engaged enough to have referred someone

        Instant last = lead.getLastContactAt();
        if (last != null) {
            if (last.isAfter(Instant.now().minus(3, ChronoUnit.DAYS))) pts += 20;
            else if (last.isAfter(Instant.now().minus(14, ChronoUnit.DAYS))) pts += 5;
        }

        pts = Math.max(0, Math.min(100, pts));
        Temperature t = pts >= 60 ? Temperature.HOT : pts >= 30 ? Temperature.WARM : Temperature.COLD;
        return new Score(pts, t);
    }
}
