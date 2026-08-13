package com.carle7.energytracker.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// UsageRepositoryImpl builds its SQL by formatting a shared template with each Granularity's
// expression rather than hand-copying one query per grain - this asserts the formatted result
// for every grain still contains the exact JOIN/WHERE fragments and SELECT/GROUP BY/ORDER BY
// expressions the queries it replaced used, so a future template edit that breaks one grain (or
// an unescaped '%' landmine) fails here rather than only showing up as a live data discrepancy.
class UsageRepositoryImplSqlTemplateTest {

    @Test
    void aggregateTemplateFormatsCorrectlyForEveryGranularity() {
        for (Granularity granularity : Granularity.values()) {
            String sql = UsageRepositoryImpl.AGGREGATE_TEMPLATE.formatted(granularity.sqlExpression());

            assertThat(sql).contains(granularity.sqlExpression() + " AS period");
            assertThat(sql).contains("GROUP BY mp.mpan, mp.meter_type, mp.is_export, " + granularity.sqlExpression());
            assertThat(sql).contains("ORDER BY " + granularity.sqlExpression());
            assertThat(sql).contains("JOIN utc_to_local z ON u.interval_from = z.local_time");
            assertThat(sql).contains("JOIN unit_rate_by_half_hour r ON r.valid_from = z.utc_time AND r.agreement_id = a.id");
            assertThat(sql).contains("AND z.local_time >= :fromDate");
            assertThat(sql).contains("AND z.local_time < :toDate");
            assertThat(sql).contains("AND r.payment_method IN (:paymentMethods)");
        }
    }

    @Test
    void breakdownTemplateFormatsCorrectlyForEveryGranularity() {
        for (Granularity granularity : Granularity.values()) {
            String sql = UsageRepositoryImpl.BREAKDOWN_TEMPLATE.formatted(granularity.sqlExpression());

            assertThat(sql).contains(granularity.sqlExpression() + " AS period");
            assertThat(sql).contains("GROUP BY mp.mpan, " + granularity.sqlExpression() + ", r.rate_type, r.value_inc_vat");
            assertThat(sql).contains("ORDER BY " + granularity.sqlExpression());
            assertThat(sql).contains("JOIN unit_rate_by_half_hour r ON r.agreement_id = a.id");
            assertThat(sql).contains("JOIN utc_to_local z ON r.valid_from = z.utc_time");
            assertThat(sql).contains("LEFT JOIN usage u ON u.mpan = mp.mpan AND u.interval_from = z.local_time");
            assertThat(sql).contains("AND z.local_time >= :intervalFrom");
            assertThat(sql).contains("AND z.local_time < :intervalTo");
            assertThat(sql).doesNotContain("payment_method");
        }
    }
}
