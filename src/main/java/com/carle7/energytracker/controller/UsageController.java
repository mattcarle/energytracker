package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.UsageByDayProjection;
import com.carle7.energytracker.repository.UsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@RestController
public class UsageController {

    private static final List<String> DEFAULT_PAYMENT_METHODS = List.of("DIRECT_DEBIT", "NA");

    @Autowired
    private UsageRepository usageRepository;

    @GetMapping("/api/usage/by-day")
    public UsageByDayResponse getUsageByDay(
            @RequestParam String mpan,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) List<String> paymentMethods) {

        LocalDate effectiveFromDate = fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveToDate = toDate != null ? toDate : LocalDate.now().plusDays(1);
        List<String> effectivePaymentMethods = paymentMethods != null && !paymentMethods.isEmpty()
                ? paymentMethods
                : DEFAULT_PAYMENT_METHODS;

        List<UsageByDayProjection> days = usageRepository.findUsageByDay(mpan, effectiveFromDate, effectiveToDate, effectivePaymentMethods);

        return new UsageByDayResponse(days, computeTotals(days));
    }

    private UsageTotals computeTotals(List<UsageByDayProjection> days) {
        long intervalCount = 0;
        BigDecimal kwh = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (UsageByDayProjection day : days) {
            intervalCount += day.getIntervalCount();
            kwh = kwh.add(day.getKwh());
            cost = cost.add(day.getCost());
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
