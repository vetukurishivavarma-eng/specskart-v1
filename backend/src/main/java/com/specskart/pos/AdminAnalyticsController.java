package com.specskart.pos;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/analytics")
public class AdminAnalyticsController {

    private final PosAnalyticsService analytics;

    public AdminAnalyticsController(PosAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/top-products")
    public List<PosAnalyticsService.ProductRank> topProducts(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        return analytics.topProducts(storeId, from, to, metric, limit);
    }
}
