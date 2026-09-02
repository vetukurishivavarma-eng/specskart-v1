package com.specskart.faceanalysis;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic geometric face-shape classifier. Scores each candidate shape from the
 * supplied ratios; returns the best match plus a normalized confidence and the rule trace.
 * This is NOT biometric identity recognition.
 */
@Component
public class FaceShapeClassifier {

    public record Result(String faceShape, double confidence, Map<String, Double> scores, String rulesUsed) {}

    public Result classify(FaceGeometry g) {
        double lengthToWidth = 1.0 / Math.max(g.cheekboneWidthRatio(), 0.01); // face height / cheekbone width
        double foreheadVsJaw = g.foreheadWidthRatio() - g.jawWidthRatio();
        double cheekVsForehead = g.cheekboneWidthRatio() - g.foreheadWidthRatio();
        double cheekVsJaw = g.cheekboneWidthRatio() - g.jawWidthRatio();
        boolean angularJaw = g.jawAngleDeg() <= 125;
        boolean softJaw = g.jawAngleDeg() >= 140;

        Map<String, Double> s = new LinkedHashMap<>();

        // OVAL: balanced, length noticeably greater than width, gently rounded jaw
        s.put("OVAL", score(
                between(lengthToWidth, 1.4, 1.7),
                Math.abs(foreheadVsJaw) < 0.06 ? 1 : 0,
                softJaw || !angularJaw ? 0.6 : 0.2));

        // ROUND: length ~ width, soft jaw, widest at cheeks
        s.put("ROUND", score(
                between(lengthToWidth, 1.0, 1.35),
                softJaw ? 1 : 0.3,
                cheekVsForehead > 0 && cheekVsJaw > 0 ? 0.8 : 0.3));

        // SQUARE: length ~ width, angular jaw, forehead ~ jaw ~ cheek
        s.put("SQUARE", score(
                between(lengthToWidth, 1.0, 1.35),
                angularJaw ? 1 : 0,
                Math.abs(foreheadVsJaw) < 0.05 && Math.abs(cheekVsJaw) < 0.05 ? 1 : 0.3));

        // RECTANGLE / OBLONG: long face, angular jaw, similar widths
        s.put("RECTANGLE", score(
                lengthToWidth >= 1.6 ? 1 : 0.2,
                angularJaw ? 0.8 : 0.4,
                Math.abs(foreheadVsJaw) < 0.06 ? 0.9 : 0.3));

        // HEART: wide forehead, narrow jaw / chin
        s.put("HEART", score(
                foreheadVsJaw > 0.08 ? 1 : 0,
                g.chinRatio() <= 0.22 ? 0.8 : 0.4,
                g.jawWidthRatio() < g.cheekboneWidthRatio() ? 0.8 : 0.3));

        // DIAMOND: cheekbones widest, narrow forehead and jaw
        s.put("DIAMOND", score(
                cheekVsForehead > 0.05 && cheekVsJaw > 0.05 ? 1 : 0,
                between(lengthToWidth, 1.3, 1.7),
                g.foreheadWidthRatio() < g.jawWidthRatio() + 0.06 ? 0.7 : 0.3));

        // TRIANGLE: jaw widest, narrow forehead
        s.put("TRIANGLE", score(
                foreheadVsJaw < -0.08 ? 1 : 0,
                g.jawWidthRatio() > g.cheekboneWidthRatio() ? 0.8 : 0.3,
                0.5));

        String best = s.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        double total = s.values().stream().mapToDouble(Double::doubleValue).sum();
        double confidence = total <= 0 ? 0.4 : Math.min(0.98, Math.max(0.45, s.get(best) / total * 2.2));

        String trace = "lengthToWidth=%.2f foreheadVsJaw=%.3f cheekVsForehead=%.3f jawAngle=%.0f"
                .formatted(lengthToWidth, foreheadVsJaw, cheekVsForehead, g.jawAngleDeg());
        return new Result(best, round(confidence), s, trace);
    }

    private static double score(double... parts) {
        double sum = 0;
        for (double p : parts) sum += p;
        return round(sum);
    }

    private static double between(double v, double lo, double hi) {
        if (v >= lo && v <= hi) return 1.0;
        double d = v < lo ? lo - v : v - hi;
        return Math.max(0, 1 - d * 3);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
