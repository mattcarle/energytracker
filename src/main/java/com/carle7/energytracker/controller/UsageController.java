package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.RateBreakdown;
import com.carle7.energytracker.repository.UsageAggregateProjection;
import com.carle7.energytracker.repository.UsageByDayGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByDayProjection;
import com.carle7.energytracker.repository.UsageByMonthGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByMonthProjection;
import com.carle7.energytracker.repository.UsageByYearGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByYearProjection;
import com.carle7.energytracker.repository.UsageDateRangeProjection;
import com.carle7.energytracker.repository.UsageRateTypeProjection;
import com.carle7.energytracker.repository.UsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class UsageController {

    private static final List<String> DEFAULT_PAYMENT_METHODS = List.of("DIRECT_DEBIT", "NA");

    @Autowired
    private UsageRepository usageRepository;

    @GetMapping("/api/usage/date-range")
    public List<UsageDateRangeProjection> getUsageDateRange() {
        return usageRepository.findDateRangeByMpan();
    }

    @GetMapping("/api/usage/by-day")
    public UsageByDayResponse getUsageByDay(
            @RequestParam String mpan,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) List<String> paymentMethods) {

        LocalDate effectiveFromDate = effectiveFromDate(fromDate);
        LocalDate effectiveToDate = effectiveToDate(toDate);
        List<String> effectivePaymentMethods = effectivePaymentMethods(paymentMethods);

        List<UsageByDayProjection> days = usageRepository.findUsageByDay(mpan, effectiveFromDate, effectiveToDate, effectivePaymentMethods);

        List<UsageByDayGroupByRateAndRateTypeProjection> breakdownRows = usageRepository.findUsageByDayGroupByRateAndRateType(
                mpan, effectiveFromDate.atStartOfDay(), effectiveToDate.atStartOfDay());
        Map<LocalDate, List<RateBreakdown>> breakdownByDate = breakdownRows.stream()
                .collect(Collectors.groupingBy(UsageByDayGroupByRateAndRateTypeProjection::getUsageDate, Collectors.mapping(UsageController::toRateBreakdown, Collectors.toList())));

        List<UsageByDayProjection> daysWithBreakdown = days.stream()
                .map(day -> new DayProjectionWithBreakdown(day, breakdownByDate.getOrDefault(day.getUsageDate(), List.of())))
                .collect(Collectors.toList());

        return new UsageByDayResponse(daysWithBreakdown, computeTotals(days));
    }

    @GetMapping("/api/usage/by-month")
    public UsageByMonthResponse getUsageByMonth(
            @RequestParam String mpan,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) List<String> paymentMethods) {

        LocalDate effectiveFromDate = effectiveFromDate(fromDate);
        LocalDate effectiveToDate = effectiveToDate(toDate);
        List<String> effectivePaymentMethods = effectivePaymentMethods(paymentMethods);

        List<UsageByMonthProjection> months = usageRepository.findUsageByMonth(mpan, effectiveFromDate, effectiveToDate, effectivePaymentMethods);

        List<UsageByMonthGroupByRateAndRateTypeProjection> breakdownRows = usageRepository.findUsageByMonthGroupByRateAndRateType(
                mpan, effectiveFromDate.atStartOfDay(), effectiveToDate.atStartOfDay());
        Map<LocalDate, List<RateBreakdown>> breakdownByMonth = breakdownRows.stream()
                .collect(Collectors.groupingBy(UsageByMonthGroupByRateAndRateTypeProjection::getUsageMonth, Collectors.mapping(UsageController::toRateBreakdown, Collectors.toList())));

        List<UsageByMonthProjection> monthsWithBreakdown = months.stream()
                .map(month -> new MonthProjectionWithBreakdown(month, breakdownByMonth.getOrDefault(month.getUsageMonth(), List.of())))
                .collect(Collectors.toList());

        return new UsageByMonthResponse(monthsWithBreakdown, computeTotals(months));
    }

    @GetMapping("/api/usage/by-year")
    public UsageByYearResponse getUsageByYear(
            @RequestParam String mpan,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) List<String> paymentMethods) {

        LocalDate effectiveFromDate = effectiveFromDate(fromDate);
        LocalDate effectiveToDate = effectiveToDate(toDate);
        List<String> effectivePaymentMethods = effectivePaymentMethods(paymentMethods);

        List<UsageByYearProjection> years = usageRepository.findUsageByYear(mpan, effectiveFromDate, effectiveToDate, effectivePaymentMethods);

        List<UsageByYearGroupByRateAndRateTypeProjection> breakdownRows = usageRepository.findUsageByYearGroupByRateAndRateType(
                mpan, effectiveFromDate.atStartOfDay(), effectiveToDate.atStartOfDay());
        Map<LocalDate, List<RateBreakdown>> breakdownByYear = breakdownRows.stream()
                .collect(Collectors.groupingBy(UsageByYearGroupByRateAndRateTypeProjection::getUsageYear, Collectors.mapping(UsageController::toRateBreakdown, Collectors.toList())));

        List<UsageByYearProjection> yearsWithBreakdown = years.stream()
                .map(year -> new YearProjectionWithBreakdown(year, breakdownByYear.getOrDefault(year.getUsageYear(), List.of())))
                .collect(Collectors.toList());

        return new UsageByYearResponse(yearsWithBreakdown, computeTotals(years));
    }

    private static RateBreakdown toRateBreakdown(UsageRateTypeProjection row) {
        return new RateBreakdown(row.getRateType(), row.getRate(), row.getKwh());
    }

    private LocalDate effectiveFromDate(LocalDate fromDate) {
        return fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate effectiveToDate(LocalDate toDate) {
        return toDate != null ? toDate : LocalDate.now().plusDays(1);
    }

    private List<String> effectivePaymentMethods(List<String> paymentMethods) {
        return paymentMethods != null && !paymentMethods.isEmpty() ? paymentMethods : DEFAULT_PAYMENT_METHODS;
    }

    private UsageTotals computeTotals(List<? extends UsageAggregateProjection> rows) {
        long intervalCount = 0;
        BigDecimal kwh = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (UsageAggregateProjection row : rows) {
            intervalCount += row.getIntervalCount();
            kwh = kwh.add(row.getKwh());
            cost = cost.add(row.getCost());
        }
        BigDecimal avgRate = kwh.compareTo(BigDecimal.ZERO) != 0
                ? cost.divide(kwh, 6, RoundingMode.HALF_UP)
                : null;
        return new UsageTotals(intervalCount, kwh, cost, avgRate);
    }

    public static class UsageByDayResponse {
        private final List<UsageByDayProjection> days;
        private final UsageTotals totals;

        public UsageByDayResponse(List<UsageByDayProjection> days, UsageTotals totals) {
            this.days = days;
            this.totals = totals;
        }

        public List<UsageByDayProjection> getDays() {
            return days;
        }

        public UsageTotals getTotals() {
            return totals;
        }
    }

    public static class UsageByMonthResponse {
        private final List<UsageByMonthProjection> months;
        private final UsageTotals totals;

        public UsageByMonthResponse(List<UsageByMonthProjection> months, UsageTotals totals) {
            this.months = months;
            this.totals = totals;
        }

        public List<UsageByMonthProjection> getMonths() {
            return months;
        }

        public UsageTotals getTotals() {
            return totals;
        }
    }

    public static class UsageByYearResponse {
        private final List<UsageByYearProjection> years;
        private final UsageTotals totals;

        public UsageByYearResponse(List<UsageByYearProjection> years, UsageTotals totals) {
            this.years = years;
            this.totals = totals;
        }

        public List<UsageByYearProjection> getYears() {
            return years;
        }

        public UsageTotals getTotals() {
            return totals;
        }
    }

    // Interface-projection getters (mpan, kwh, etc.) come straight from the query result and
    // can't be mutated after the fact, so "breakdown" - assembled separately in Java from a
    // second query - can't just be attached to the projection Spring Data already returned.
    // These delegate every other getter to that original projection and supply the breakdown
    // list themselves, so the JSON shape stays identical to before with breakdown as a normal
    // sibling field, rather than introducing a wrapper object around each row.
    private static final class DayProjectionWithBreakdown implements UsageByDayProjection {
        private final UsageByDayProjection delegate;
        private final List<RateBreakdown> breakdown;

        private DayProjectionWithBreakdown(UsageByDayProjection delegate, List<RateBreakdown> breakdown) {
            this.delegate = delegate;
            this.breakdown = breakdown;
        }

        @Override
        public LocalDate getUsageDate() {
            return delegate.getUsageDate();
        }

        @Override
        public String getMpan() {
            return delegate.getMpan();
        }

        @Override
        public String getMeterType() {
            return delegate.getMeterType();
        }

        @Override
        public Boolean getIsExport() {
            return delegate.getIsExport();
        }

        @Override
        public Long getIntervalCount() {
            return delegate.getIntervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return delegate.getKwh();
        }

        @Override
        public BigDecimal getCost() {
            return delegate.getCost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return delegate.getAvgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return breakdown;
        }
    }

    private static final class MonthProjectionWithBreakdown implements UsageByMonthProjection {
        private final UsageByMonthProjection delegate;
        private final List<RateBreakdown> breakdown;

        private MonthProjectionWithBreakdown(UsageByMonthProjection delegate, List<RateBreakdown> breakdown) {
            this.delegate = delegate;
            this.breakdown = breakdown;
        }

        @Override
        public LocalDate getUsageMonth() {
            return delegate.getUsageMonth();
        }

        @Override
        public String getMpan() {
            return delegate.getMpan();
        }

        @Override
        public String getMeterType() {
            return delegate.getMeterType();
        }

        @Override
        public Boolean getIsExport() {
            return delegate.getIsExport();
        }

        @Override
        public Long getIntervalCount() {
            return delegate.getIntervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return delegate.getKwh();
        }

        @Override
        public BigDecimal getCost() {
            return delegate.getCost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return delegate.getAvgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return breakdown;
        }
    }

    private static final class YearProjectionWithBreakdown implements UsageByYearProjection {
        private final UsageByYearProjection delegate;
        private final List<RateBreakdown> breakdown;

        private YearProjectionWithBreakdown(UsageByYearProjection delegate, List<RateBreakdown> breakdown) {
            this.delegate = delegate;
            this.breakdown = breakdown;
        }

        @Override
        public LocalDate getUsageYear() {
            return delegate.getUsageYear();
        }

        @Override
        public String getMpan() {
            return delegate.getMpan();
        }

        @Override
        public String getMeterType() {
            return delegate.getMeterType();
        }

        @Override
        public Boolean getIsExport() {
            return delegate.getIsExport();
        }

        @Override
        public Long getIntervalCount() {
            return delegate.getIntervalCount();
        }

        @Override
        public BigDecimal getKwh() {
            return delegate.getKwh();
        }

        @Override
        public BigDecimal getCost() {
            return delegate.getCost();
        }

        @Override
        public BigDecimal getAvgRate() {
            return delegate.getAvgRate();
        }

        @Override
        public List<RateBreakdown> getBreakdown() {
            return breakdown;
        }
    }

    public static class UsageTotals {
        private final long intervalCount;
        private final BigDecimal kwh;
        private final BigDecimal cost;
        private final BigDecimal avgRate;

        public UsageTotals(long intervalCount, BigDecimal kwh, BigDecimal cost, BigDecimal avgRate) {
            this.intervalCount = intervalCount;
            this.kwh = kwh;
            this.cost = cost;
            this.avgRate = avgRate;
        }

        public long getIntervalCount() {
            return intervalCount;
        }

        public BigDecimal getKwh() {
            return kwh;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public BigDecimal getAvgRate() {
            return avgRate;
        }
    }
}
