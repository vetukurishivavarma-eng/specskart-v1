package com.specskart.pos;

import com.specskart.catalog.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ranks products by revenue, profit, or units sold over a date range — same three metrics
 *  NG POS's "Top 20 products" ranker offers. */
@Service
public class PosAnalyticsService {

    private final PosSaleRepository sales;
    private final PosSaleItemRepository saleItems;
    private final ProductRepository products;

    public PosAnalyticsService(PosSaleRepository sales, PosSaleItemRepository saleItems, ProductRepository products) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.products = products;
    }

    public record ProductRank(UUID productId, String productName, long revenueMinor, long profitMinor, int quantity) {}

    @Transactional(readOnly = true)
    public List<ProductRank> topProducts(UUID storeId, LocalDate from, LocalDate to, String metric, int limit) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<UUID, long[]> totals = new HashMap<>(); // [revenueMinor, quantity]
        for (PosSale sale : sales.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(storeId, fromInstant, toInstant)) {
            if (!"SALE".equals(sale.getSaleType()) || sale.isVoided()) continue;
            for (PosSaleItem item : saleItems.findBySaleId(sale.getId())) {
                if (item.getProductId() == null) continue;
                long[] t = totals.computeIfAbsent(item.getProductId(), k -> new long[2]);
                t[0] += item.getLineTotalMinor();
                t[1] += item.getQuantity();
            }
        }

        List<ProductRank> ranked = totals.entrySet().stream()
                .map(e -> {
                    var product = products.findById(e.getKey()).orElse(null);
                    long revenue = e.getValue()[0];
                    int qty = (int) e.getValue()[1];
                    long cost = product == null ? 0 : product.getCostPriceMinor() * qty;
                    return new ProductRank(e.getKey(), product == null ? "Unknown product" : product.getName(),
                            revenue, revenue - cost, qty);
                })
                .sorted((a, b) -> switch (metric) {
                    case "profit" -> Long.compare(b.profitMinor(), a.profitMinor());
                    case "quantity" -> Integer.compare(b.quantity(), a.quantity());
                    default -> Long.compare(b.revenueMinor(), a.revenueMinor());
                })
                .limit(limit)
                .toList();
        return ranked;
    }
}
