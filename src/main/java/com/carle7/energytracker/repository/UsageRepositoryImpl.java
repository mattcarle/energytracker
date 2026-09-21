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
    // hh (HAPPY_HOUR) is LEFT JOINed against z.local_time, the same local-time column already
    // used for every other boundary check here - COALESCE(hh.rate * 100, r.value_inc_vat) prefers
    // the happy-hour rate (stored in £, so *100 puts it on the same pence scale as
    // value_inc_vat) whenever the half-hour falls in one, falling back to the ordinary unit rate
    // otherwise. HappyHourController rejects overlapping rows at write time, so this join can
    // never match more than one hh row per half-hour. Happy hours are an electricity-import
    // offer, so the join is also restricted to import electricity meter points (mp.meter_type =
    // 'ELEC' AND NOT mp.is_export) - a happy-hour row has no meter point of its own, and without
    // this gas and export usage that fell in the window was rated at the happy-hour rate too. The
    // same restriction is on the two templates below.
    static final String AGGREGATE_TEMPLATE = """
            SELECT mp.mpan AS mpan,
                   mp.meter_type AS meterType,
                   mp.is_export AS isExport,
                   %1$s AS period,
                   COUNT(*) AS intervalCount,
                   SUM(CASE WHEN u.missing THEN 1 ELSE 0 END) AS missingIntervalCount,
                   SUM(u.consumption) AS kwh,
                   SUM(u.consumption * COALESCE(hh.rate * 100, r.value_inc_vat) / 100) AS cost,
                   SUM(u.consumption * COALESCE(hh.rate * 100, r.value_inc_vat) / 100) / NULLIF(SUM(u.consumption), 0) AS avgRate
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN utc_to_local z ON u.interval_from = z.local_time
                     JOIN unit_rate_by_half_hour r ON r.valid_from = z.utc_time AND r.agreement_id = a.id
                     LEFT JOIN happy_hour hh ON z.local_time >= hh.valid_from AND z.local_time < hh.valid_to
                          AND mp.meter_type = 'ELEC' AND mp.is_export = FALSE
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND r.payment_method IN (:paymentMethods)
            GROUP BY mp.mpan, mp.meter_type, mp.is_export, %1$s
            ORDER BY %1$s
            """;

    // Driven from unit_rate_by_half_hour (via utc_to_local) rather than usage, with usage
    // LEFT JOINed in - so a half-hour with a rate but no recorded consumption (e.g. today's
    // not-yet-synced intervals) still produces a row, with kwh coalesced to 0 instead of being
    // dropped entirely as an INNER JOIN through usage would do. A happy-hour half-hour is
    // reported as its own synthetic 'HAPPY_HOUR' rate type (rather than keeping the underlying
    // tariff's own STANDARD/DAY/NIGHT type) so it renders as a distinct breakdown segment even
    // when its discounted rate happens to land close to the ordinary rate.
    static final String BREAKDOWN_TEMPLATE = """
            SELECT mp.mpan AS mpan,
                   %1$s AS period,
                   CASE WHEN hh.id IS NOT NULL THEN 'HAPPY_HOUR' ELSE r.rate_type END AS rateType,
                   COALESCE(hh.rate * 100, r.value_inc_vat) AS rate,
                   COALESCE(SUM(u.consumption), 0) AS kwh
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN unit_rate_by_half_hour r ON r.agreement_id = a.id
                     JOIN utc_to_local z ON r.valid_from = z.utc_time
                     LEFT JOIN usage u ON u.mpan = mp.mpan AND u.interval_from = z.local_time
                     LEFT JOIN happy_hour hh ON z.local_time >= hh.valid_from AND z.local_time < hh.valid_to
                          AND mp.meter_type = 'ELEC' AND mp.is_export = FALSE
            WHERE mp.mpan = :mpan
              AND z.local_time >= :intervalFrom
              AND z.local_time < :intervalTo
            GROUP BY mp.mpan, %1$s, r.rate_type, r.value_inc_vat, hh.id, hh.rate
            ORDER BY %1$s
            """;

    // INNER (not LEFT) JOINed to happy_hour, unlike the two templates above - only half-hours
    // that actually fell in a happy hour window contribute here. moneySaved is the ordinary
    // unit rate minus the happy-hour rate (both put on the same pence scale, see
    // AGGREGATE_TEMPLATE's comment) times consumption, i.e. what that usage would have cost
    // without the discount, minus what it actually cost.
    static final String HAPPY_HOUR_SAVINGS_TEMPLATE = """
            SELECT COALESCE(SUM(u.consumption), 0) AS kwh,
                   COALESCE(SUM(u.consumption * (r.value_inc_vat - hh.rate * 100) / 100), 0) AS moneySaved
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN utc_to_local z ON u.interval_from = z.local_time
                     JOIN unit_rate_by_half_hour r ON r.valid_from = z.utc_time AND r.agreement_id = a.id
                     JOIN happy_hour hh ON z.local_time >= hh.valid_from AND z.local_time < hh.valid_to
                          AND mp.meter_type = 'ELEC' AND mp.is_export = FALSE
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND r.payment_method IN (:paymentMethods)
            """;

    private final EntityManager entityManager;

    public UsageRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public HappyHourSavingsProjection findHappyHourSavings(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        Query query = entityManager.createNativeQuery(HAPPY_HOUR_SAVINGS_TEMPLATE, Tuple.class);
        query.setParameter("mpan", mpan);
        query.setParameter("fromDate", fromDate);
        query.setParameter("toDate", toDate);
        query.unwrap(NativeQuery.class).setParameterList("paymentMethods", paymentMethods);
        // A scalar aggregate query (no GROUP BY) always returns exactly one row, even when no
        // usage fell in a happy hour - the COALESCEs above turn its NULL sums into zero.
        Tuple row = toTupleList(query).get(0);
        return new HappyHourSavingsRow(row.get("kwh", BigDecimal.class), row.get("moneySaved", BigDecimal.class));
    }

    private record HappyHourSavingsRow(BigDecimal kwh, BigDecimal moneySaved) implements HappyHourSavingsProjection {
        @Override
        public BigDecimal getKwh() {
            return kwh;
        }

        @Override
        public BigDecimal getMoneySaved() {
            return moneySaved;
        }
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
    public List<UsageByWeekProjection> findUsageByWeek(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods) {
        return queryAggregated(Granularity.WEEK, mpan, fromDate, toDate, paymentMethods).stream()
                .map(t -> AggregateRow.from(t, LocalDate.class))
                .<UsageByWeekProjection>map(WeekAggregateProjection::new)
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
    public List<UsageByWeekGroupByRateAndRateTypeProjection> findUsageByWeekGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo) {
        return queryBreakdown(Granularity.WEEK, mpan, intervalFrom, intervalTo).stream()
                .map(t -> RateTypeRow.from(t, LocalDate.class))
                .<UsageByWeekGroupByRateAndRateTypeProjection>map(WeekRateTypeProjection::new)
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
                                    Long intervalCount, Long missingIntervalCount, BigDecimal kwh, BigDecimal cost,
                                    BigDecimal avgRate) {
        static <P> AggregateRow<P> from(Tuple t, Class<P> periodType) {
            return new AggregateRow<>(
                    t.get("mpan", String.class),
                    t.get("meterType", String.class),
                    t.get("isExport", Boolean.class),
                    t.get("period", periodType),
                    t.get("intervalCount", Long.class),
                    t.get("missingIntervalCount", Long.class),
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
        public Long getMissingIntervalCount() {
            return row.missingIntervalCount();
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
        public Long getMissingIntervalCount() {
            return row.missingIntervalCount();
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

    private record WeekAggregateProjection(AggregateRow<LocalDate> row) implements UsageByWeekProjection {
        @Override
        public LocalDate getUsageWeek() {
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
        public Long getMissingIntervalCount() {
            return row.missingIntervalCount();
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
        public Long getMissingIntervalCount() {
            return row.missingIntervalCount();
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
        public Long getMissingIntervalCount() {
            return row.missingIntervalCount();
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

    private record WeekRateTypeProjection(RateTypeRow<LocalDate> row) implements UsageByWeekGroupByRateAndRateTypeProjection {
        @Override
        public LocalDate getUsageWeek() {
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
