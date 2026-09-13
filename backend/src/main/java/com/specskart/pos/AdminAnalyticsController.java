package com.specskart.pos;

import com.specskart.shared.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
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
    private final CurrentUser currentUser;

    public AdminAnalyticsController(PosAnalyticsService analytics, CurrentUser currentUser) {
        this.analytics = analytics;
        this.currentUser = currentUser;
    }

    @GetMapping("/top-products")
    public List<PosAnalyticsService.ProductRank> topProducts(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        return analytics.topProducts(storeId, from, to, metric, limit);
    }
}
