package com.specskart.pos;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pos/reports/day")
public class AdminDailyReportController {

    private final DailyReportService reports;

    public AdminDailyReportController(DailyReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public DailyReportService.DayReportView get(@RequestParam UUID storeId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.generate(storeId, date);
    }
}
