package com.specskart.pos;

import com.specskart.shared.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
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
    private final CurrentUser currentUser;

    public AdminDailyReportController(DailyReportService reports, CurrentUser currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @GetMapping
    public DailyReportService.DayReportView get(@RequestParam UUID storeId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 Authentication auth) {
        currentUser.assertStoreAccess(auth, storeId);
        return reports.generate(storeId, date);
    }
}
