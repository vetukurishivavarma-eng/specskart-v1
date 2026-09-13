package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A snapshot Z-report for one store, one day. Regenerated on request (see
 *  DailyReportService) rather than a scheduled nightly job — this app is small enough that
 *  "always current when you look at it" beats a frozen-at-midnight figure; {@code finalized}
 *  is carried in the schema for later if that changes, unused for now. */
@Entity
@Table(name = "daily_reports")
@Getter
@Setter
public class DailyReport extends BaseEntity {

    @Column(nullable = false)
    private UUID storeId;

    /** YYYY-MM-DD, local calendar date. */
    @Column(nullable = false)
    private String reportDate;

    @Column(nullable = false)
    private int saleCount;
    @Column(nullable = false)
    private long grossTotalMinor;
    @Column(nullable = false)
    private long cashTotalMinor;
    @Column(nullable = false)
    private long cardTotalMinor;
    @Column(nullable = false)
    private long mobileTotalMinor;

    /** JSON: [{name, quantity, total}], best sellers first. */
    @Column(nullable = false, length = 4000)
    private String topItems = "[]";

    @Column(nullable = false)
    private boolean finalized = false;
}
