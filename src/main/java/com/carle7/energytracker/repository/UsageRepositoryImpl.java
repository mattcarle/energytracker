package com.carle7.energytracker.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.hibernate.query.NativeQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// UsageRepository's 6 aggregate/breakdown queries used to be 6 hand-copied native @Query
// methods, differing only in their date-truncation grain (day/month/year) - a recent change
// (reverting UTC grouping back to local time) had to be applied to all 6 by hand, twice. This
// builds every query from one of two shared SQL templates instead, parameterized by
// Granularity, so that kind of change only needs to happen in one place.
//
// This is the project's first use of EntityManager/native-Query directly rather than a plain
// @Query-annotated method - Spring Data's own IN-list parameter expansion (a List<String>
// bound to :paymentMethods becoming IN (?,?,?)) only happens for @Query methods, not for
// EntityManager native queries, so that binding goes through Hibernate's NativeQuery
// .setParameterList(...) instead of the standard JPA Query.setParameter(...).
public class UsageRepositoryImpl implements UsageRepositoryCustom {

    // Every occurrence of %1$s is the same date-truncation expression - repeated once for the
    // SELECT alias, once for GROUP BY, once for ORDER BY, exactly as the queries this replaces
    // did. FROM/JOIN/WHERE text is unchanged from those queries so the generated SQL is the
    // same shape as before, just assembled once per family instead of once per grain.
    // Package-private (not private) so UsageRepositoryImplSqlTemplateTest can assert the
    // formatted SQL text directly.
    static final String AGGREGATE_TEMPLATE = """
            SELECT mp.mpan AS mpan,
                   mp.meter_type AS meterType,
                   mp.is_export AS isExport,
                   %1$s AS period,
                   COUNT(*) AS intervalCount,
                   SUM(u.consumption) AS kwh,
                   SUM(u.consumption * r.value_inc_vat / 100) AS cost,
                   SUM(u.consumption * r.value_inc_vat / 100) / NULLIF(SUM(u.consumption), 0) AS avgRate
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN utc_to_local z ON u.interval_from = z.local_time
                     JOIN unit_rate_by_half_hour r ON r.valid_from = z.utc_time AND r.agreement_id = a.id
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND r.payment_method IN (:paymentMethods)
            GROUP BY mp.mpan, mp.meter_type, mp.is_export, %1$s
            ORDER BY %1$s
            """;

    static final String BREAKDOWN_TEMPLATE = """
            SELECT mp.mpan AS mpan,
                   %1$s AS period,
                   r.rate_type AS rateType,
                   r.value_inc_vat AS rate,
                   SUM(u.consumption) AS kwh
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN utc_to_local z ON u.interval_from = z.local_time
                     JOIN unit_rate_by_half_hour r ON r.valid_from = z.utc_time AND r.agreement_id = a.id
            WHERE mp.mpan = :mpan
              AND z.local_time >= :intervalFrom
              AND z.local_time < :intervalTo
            GROUP BY mp.mpan, %1$s, r.rate_type, r.value_inc_vat
            ORDER BY %1$s
            """;

    private final EntityManager entityManager;

    public UsageRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<UsageByHalfHourProjection> findUsageByHalfHour(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        return queryAggregated(Granularity.HALF_HOUR, mpan, fromDate, toDate, paymentMethods).stream()
                .map(t -> AggregateRow.from(t, LocalDateTime.class))
                .<UsageByHalfHourProjection>map(HalfHourAggregateProjection::new)
                .toList();
    }

    @Override
    public List<UsageByDayProjection> findUsageByDay(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        return queryAggregated(Granularity.DAY, mpan, fromDate, toDate, paymentMethods).stream()
                .map(t -> AggregateRow.from(t, LocalDate.class))
                .<UsageByDayProjection>map(DayAggregateProjection::new)
                .toList();
    }

    @Override
    public List<UsageByMonthProjection> findUsageByMonth(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        return queryAggregated(Granularity.MONTH, mpan, fromDate, toDate, paymentMethods).stream()
                .map(t -> AggregateRow.from(t, LocalDate.class))
                .<UsageByMonthProjection>map(MonthAggregateProjection::new)
                .toList();
    }

    @Override
    public List<UsageByYearProjection> findUsageByYear(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        return queryAggregated(Granularity.YEAR, mpan, fromDate, toDate, paymentMethods).stream()
                .map(t -> AggregateRow.from(t, LocalDate.class))
                .<UsageByYearProjection>map(YearAggregateProjection::new)
                .toList();
    }

    @Override
    public List<UsageByHalfHourGroupByRateAndRateTypeProjection> findUsageByHalfHourGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo) {
        return queryBreakdown(Granularity.HALF_HOUR, mpan, intervalFrom, intervalTo).stream()
                .map(t -> RateTypeRow.from(t, LocalDateTime.class))
                .<UsageByHalfHourGroupByRateAndRateTypeProjection>map(HalfHourRateTypeProjection::new)
                .toList();
    }

    @Override
    public List<UsageByDayGroupByRateAndRateTypeProjection> findUsageByDayGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo) {
        return queryBreakdown(Granularity.DAY, mpan, intervalFrom, intervalTo).stream()
                .map(t -> RateTypeRow.from(t, LocalDate.class))
                .<UsageByDayGroupByRateAndRateTypeProjection>map(DayRateTypeProjection::new)
                .toList();
    }

    @Override
    public List<UsageByMonthGroupByRateAndRateTypeProjection> findUsageByMonthGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo) {
        return queryBreakdown(Granularity.MONTH, mpan, intervalFrom, intervalTo).stream()
                .map(t -> RateTypeRow.from(t, LocalDate.class))
                .<UsageByMonthGroupByRateAndRateTypeProjection>map(MonthRateTypeProjection::new)
                .toList();
    }

    @Override
    public List<UsageByYearGroupByRateAndRateTypeProjection> findUsageByYearGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo) {
        return queryBreakdown(Granularity.YEAR, mpan, intervalFrom, intervalTo).stream()
                .map(t -> RateTypeRow.from(t, LocalDate.class))
                .<UsageByYearGroupByRateAndRateTypeProjection>map(YearRateTypeProjection::new)
                .toList();
    }

    private List<Tuple> queryAggregated(Granularity granularity, String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        String sql = AGGREGATE_TEMPLATE.formatted(granularity.sqlExpression());
        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        query.setParameter("mpan", mpan);
        query.setParameter("fromDate", fromDate);
        query.setParameter("toDate", toDate);
        // Spring Data's List -> IN (?,?,?) expansion only applies to its own @Query-derived
        // methods - a plain Query.setParameter("paymentMethods", list) here would fail against
        // JDBC, so the IN-list goes through Hibernate's native-query-specific API instead.
        query.unwrap(NativeQuery.class).setParameterList("paymentMethods", paymentMethods);
        return toTupleList(query);
    }

    private List<Tuple> queryBreakdown(Granularity granularity, String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo) {
        String sql = BREAKDOWN_TEMPLATE.formatted(granularity.sqlExpression());
        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        query.setParameter("mpan", mpan);
        query.setParameter("intervalFrom", intervalFrom);
        query.setParameter("intervalTo", intervalTo);
        return toTupleList(query);
    }

    private static List<Tuple> toTupleList(Query query) {
        return query.getResultList().stream().map(Tuple.class::cast).toList();
    }

    // Generic over the period column's Java type - LocalDate for DAY/MONTH/YEAR (all CAST(...
    // AS DATE)), LocalDateTime for HALF_HOUR (the raw, untruncated timestamp). The grain itself
    // only ever determines the SQL expression (see Granularity); this is the one place that
    // distinction shows up on the Java side.
    private record AggregateRow<P>(String mpan, String meterType, Boolean isExport, P period,
                                    Long intervalCount, BigDecimal kwh, BigDecimal cost, BigDecimal avgRate) {
        static <P> AggregateRow<P> from(Tuple t, Class<P> periodType) {
            return new AggregateRow<>(
                    t.get("mpan", String.class),
                    t.get("meterType", String.class),
                    t.get("isExport", Boolean.class),
                    t.get("period", periodType),
                    t.get("intervalCount", Long.class),
                    t.get("kwh", BigDecimal.class),
                    t.get("cost", BigDecimal.class),
                    t.get("avgRate", BigDecimal.class));
        }
    }

    private record RateTypeRow<P>(String mpan, P period, String rateType, BigDecimal rate, BigDecimal kwh) {
        static <P> RateTypeRow<P> from(Tuple t, Class<P> periodType) {
            return new RateTypeRow<>(
                    t.get("mpan", String.class),
                    t.get("period", periodType),
                    t.get("rateType", String.class),
                    t.get("rate", BigDecimal.class),
                    t.get("kwh", BigDecimal.class));
        }
    }

    // getBreakdown() is never called on a raw repository row today - UsageController always
    // re-wraps rows (see its *WithBreakdown classes) with the real breakdown, assembled from a
    // separate query, before touching getBreakdown()/getKwhOffPeak()/getCostOffPeak(). Returning
    // null here mirrors that dead path rather than guessing at a value, and is handled safely -
    // UsageAggregateProjection's off-peak default methods already null-check the breakdown.
    private record HalfHourAggregateProjection(AggregateRow<LocalDateTime> row) implements UsageByHalfHourProjection {
        @Override
        public LocalDateTime getUsageInterval() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getMeterType() {
            return row.meterType();
        }

        @Override
        public Boolean getIsExport() {
            return row.isExport();
        }

        @Override
        public Long getIntervalCount() {
            return row.intervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }

        @Override
        public BigDecimal getCost() {
            return row.cost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return row.avgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return null;
        }
    }

    private record DayAggregateProjection(AggregateRow<LocalDate> row) implements UsageByDayProjection {
        @Override
        public LocalDate getUsageDate() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getMeterType() {
            return row.meterType();
        }

        @Override
        public Boolean getIsExport() {
            return row.isExport();
        }

        @Override
        public Long getIntervalCount() {
            return row.intervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }

        @Override
        public BigDecimal getCost() {
            return row.cost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return row.avgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return null;
        }
    }

    private record MonthAggregateProjection(AggregateRow<LocalDate> row) implements UsageByMonthProjection {
        @Override
        public LocalDate getUsageMonth() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getMeterType() {
            return row.meterType();
        }

        @Override
        public Boolean getIsExport() {
            return row.isExport();
        }

        @Override
        public Long getIntervalCount() {
            return row.intervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }

        @Override
        public BigDecimal getCost() {
            return row.cost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return row.avgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return null;
        }
    }

    private record YearAggregateProjection(AggregateRow<LocalDate> row) implements UsageByYearProjection {
        @Override
        public LocalDate getUsageYear() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getMeterType() {
            return row.meterType();
        }

        @Override
        public Boolean getIsExport() {
            return row.isExport();
        }

        @Override
        public Long getIntervalCount() {
            return row.intervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }

        @Override
        public BigDecimal getCost() {
            return row.cost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return row.avgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return null;
        }
    }

    private record HalfHourRateTypeProjection(RateTypeRow<LocalDateTime> row) implements UsageByHalfHourGroupByRateAndRateTypeProjection {
        @Override
        public LocalDateTime getUsageInterval() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getRateType() {
            return row.rateType();
        }

        @Override
        public BigDecimal getRate() {
            return row.rate();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }
    }

    private record DayRateTypeProjection(RateTypeRow<LocalDate> row) implements UsageByDayGroupByRateAndRateTypeProjection {
        @Override
        public LocalDate getUsageDate() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getRateType() {
            return row.rateType();
        }

        @Override
        public BigDecimal getRate() {
            return row.rate();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }
    }

    private record MonthRateTypeProjection(RateTypeRow<LocalDate> row) implements UsageByMonthGroupByRateAndRateTypeProjection {
        @Override
        public LocalDate getUsageMonth() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getRateType() {
            return row.rateType();
        }

        @Override
        public BigDecimal getRate() {
            return row.rate();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }
    }

    private record YearRateTypeProjection(RateTypeRow<LocalDate> row) implements UsageByYearGroupByRateAndRateTypeProjection {
        @Override
        public LocalDate getUsageYear() {
            return row.period();
        }

        @Override
        public String getMpan() {
            return row.mpan();
        }

        @Override
        public String getRateType() {
            return row.rateType();
        }

        @Override
        public BigDecimal getRate() {
            return row.rate();
        }

        @Override
        public BigDecimal getKwh() {
            return row.kwh();
        }
    }
}
