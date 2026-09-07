package com.specskart.order;

import com.specskart.faceanalysis.FaceAnalysisRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Lightweight, privacy-safe "others are shopping" signal for the storefront. Public. */
@RestController
@RequestMapping("/api/public/social-proof")
public class SocialProofController {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final FaceAnalysisRepository analyses;

    public SocialProofController(OrderRepository orders, OrderItemRepository items, FaceAnalysisRepository analyses) {
        this.orders = orders;
        this.items = items;
        this.analyses = analyses;
    }

    public record RecentBuy(String name, String city, String item, String ago) {}
    public record SocialProof(long analysesThisWeek, long ordersThisWeek, List<RecentBuy> recent) {}

    @GetMapping
    public SocialProof get() {
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        List<RecentBuy> recent = orders.findTop6ByPaidAtIsNotNullOrderByPaidAtDesc().stream()
                .map(this::toRecent)
                .toList();
        return new SocialProof(
                analyses.countByCreatedAtAfter(weekAgo),
                orders.countByPaidAtAfter(weekAgo),
                recent);
    }

    private RecentBuy toRecent(Order o) {
        String first = o.getCustomerName() == null ? "Someone" : o.getCustomerName().trim().split("\\s+")[0];
        String item = items.findByOrderId(o.getId()).stream().findFirst()
                .map(OrderItem::getProductName).orElse("a new pair");
        return new RecentBuy(first, o.getShipCity(), item, ago(o.getPaidAt()));
    }

    private static String ago(Instant t) {
        if (t == null) return "recently";
        long mins = Duration.between(t, Instant.now()).toMinutes();
        if (mins < 60) return Math.max(1, mins) + " min ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + "h ago";
        return (hrs / 24) + "d ago";
    }
}
