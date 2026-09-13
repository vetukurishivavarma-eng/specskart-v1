package com.specskart.pos;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The day's Z-report: total takings, split by payment method, best sellers. Regenerated
 *  fresh on every request from pos_sales/pos_sale_items/pos_payments — see {@link DailyReport}
 *  for why this isn't a scheduled nightly job. */
@Service
public class DailyReportService {

    private final PosSaleRepository sales;
    private final PosSaleItemRepository saleItems;
    private final PosPaymentRepository payments;
    private final DailyReportRepository reports;
    private final ObjectMapper mapper;

    public DailyReportService(PosSaleRepository sales, PosSaleItemRepository saleItems,
                              PosPaymentRepository payments, DailyReportRepository reports, ObjectMapper mapper) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.payments = payments;
        this.reports = reports;
        this.mapper = mapper;
    }

    public record TopItem(String name, int quantity, long totalMinor) {}
    public record DayReportView(UUID storeId, String reportDate, int saleCount, long grossTotalMinor,
                                long cashTotalMinor, long cardTotalMinor, long mobileTotalMinor,
                                List<TopItem> topItems) {}

    @Transactional
    public DayReportView generate(UUID storeId, LocalDate date) {
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = from.plus(Duration.ofDays(1));
        var daySales = sales.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(storeId, from, to).stream()
                .filter(s -> !"VOIDED".equals(s.getStatus())).toList();

        long gross = 0, cash = 0, card = 0, mobile = 0;
        Map<String, TopItemAcc> byProduct = new LinkedHashMap<>();
        for (PosSale sale : daySales) {
            gross += sale.getTotalMinor();
            for (PosPayment p : payments.findBySaleId(sale.getId())) {
                switch (p.getMethod()) {
                    case "CASH" -> cash += p.getAmountMinor();
                    case "CARD" -> card += p.getAmountMinor();
                    case "MOBILE" -> mobile += p.getAmountMinor();
                    default -> {}
                }
            }
            for (PosSaleItem item : saleItems.findBySaleId(sale.getId())) {
                byProduct.computeIfAbsent(item.getProductName(), k -> new TopItemAcc())
                        .add(item.getQuantity(), item.getLineTotalMinor());
            }
        }

        List<TopItem> topItems = byProduct.entrySet().stream()
                .map(e -> new TopItem(e.getKey(), e.getValue().quantity, e.getValue().totalMinor))
                .sorted((a, b) -> Long.compare(b.totalMinor(), a.totalMinor()))
                .limit(5)
                .toList();

        String reportDate = date.toString();
        DailyReport report = reports.findByStoreIdAndReportDate(storeId, reportDate).orElseGet(() -> {
            DailyReport r = new DailyReport();
            r.setStoreId(storeId);
            r.setReportDate(reportDate);
            return r;
        });
        report.setSaleCount(daySales.size());
        report.setGrossTotalMinor(gross);
        report.setCashTotalMinor(cash);
        report.setCardTotalMinor(card);
        report.setMobileTotalMinor(mobile);
        report.setTopItems(mapper.writeValueAsString(topItems));
        reports.save(report);

        return new DayReportView(storeId, reportDate, daySales.size(), gross, cash, card, mobile, topItems);
    }

    private static final class TopItemAcc {
        int quantity;
        long totalMinor;
        void add(int qty, long total) { quantity += qty; totalMinor += total; }
    }
}
