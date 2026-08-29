package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.SolarGeneration;
import com.carle7.energytracker.repository.SolarByPeriodProjection;
import com.carle7.energytracker.repository.SolarDateRangeProjection;
import com.carle7.energytracker.repository.SolarGenerationRepository;
import com.carle7.energytracker.service.GrowattApiService.PlantDataDto;
import com.carle7.energytracker.service.GrowattApiService.PlantPowerData;
import com.carle7.energytracker.service.GrowattApiService.PowerPointDto;
import com.carle7.energytracker.service.GrowattCredentialsService;
import com.carle7.energytracker.service.GrowattService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@RestController
public class SolarController {

    @Autowired
    private SolarGenerationRepository solarGenerationRepository;

    @Autowired
    private GrowattCredentialsService growattCredentialsService;

    @Autowired
    private GrowattService growattService;

    @GetMapping("/api/solar/date-range")
    public List<SolarDateRangeProjection> getSolarDateRange() {
        return solarGenerationRepository.findDateRangeByPlantId();
    }

    @GetMapping("/api/solar/by-day")
    public SolarByDayResponse getSolarByDay(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {

        String plantId = resolvePlantId();
        if (plantId == null) {
            return new SolarByDayResponse(List.of(), emptyTotals());
        }

        LocalDate effectiveFromDate = effectiveFromDate(fromDate);
        LocalDate effectiveToDate = effectiveToDate(toDate);

        List<SolarGeneration> rows = solarGenerationRepository
                .findByPlantIdAndGenerationDateGreaterThanEqualAndGenerationDateLessThanOrderByGenerationDateAsc(
                        plantId, effectiveFromDate, effectiveToDate);

        List<SolarDayEntry> days = rows.stream()
                .map(r -> new SolarDayEntry(r.getGenerationDate(), r.getEnergyKwh()))
                .toList();

        return new SolarByDayResponse(days, computeTotalsFromDays(days));
    }

    @GetMapping("/api/solar/by-week")
    public SolarByWeekResponse getSolarByWeek(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {

        String plantId = resolvePlantId();
        if (plantId == null) {
            return new SolarByWeekResponse(List.of(), emptyTotals());
        }

        List<SolarPeriodEntry> weeks = toPeriodEntries(
                solarGenerationRepository.findByWeek(plantId, effectiveFromDate(fromDate), effectiveToDate(toDate)));
        return new SolarByWeekResponse(weeks, computeTotalsFromPeriods(weeks));
    }

    @GetMapping("/api/solar/by-month")
    public SolarByMonthResponse getSolarByMonth(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {

        String plantId = resolvePlantId();
        if (plantId == null) {
            return new SolarByMonthResponse(List.of(), emptyTotals());
        }

        List<SolarPeriodEntry> months = toPeriodEntries(
                solarGenerationRepository.findByMonth(plantId, effectiveFromDate(fromDate), effectiveToDate(toDate)));
        return new SolarByMonthResponse(months, computeTotalsFromPeriods(months));
    }

    @GetMapping("/api/solar/by-year")
    public SolarByYearResponse getSolarByYear(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {

        String plantId = resolvePlantId();
        if (plantId == null) {
            return new SolarByYearResponse(List.of(), emptyTotals());
        }

        List<SolarPeriodEntry> years = toPeriodEntries(
                solarGenerationRepository.findByYear(plantId, effectiveFromDate(fromDate), effectiveToDate(toDate)));
        return new SolarByYearResponse(years, computeTotalsFromPeriods(years));
    }

    // Live proxy, not persisted - see the plan's note on why intraday power isn't backfilled.
    @GetMapping("/api/solar/hourly")
    public SolarHourlyResponse getSolarHourly(@RequestParam LocalDate date) {
        PlantPowerData data = growattService.getLivePowerCurve(date);
        if (data == null || data.powers == null) {
            return new SolarHourlyResponse(List.of());
        }
        // Growatt returns powers[] in arbitrary order, not chronological - time is
        // "yyyy-MM-dd HH:mm" (fixed-width, zero-padded), so plain string ordering sorts it
        // correctly without needing a date-time parse.
        List<PowerPoint> points = data.powers.stream()
                .sorted(Comparator.comparing(dto -> dto.time))
                .map(SolarController::toPowerPoint)
                .toList();
        return new SolarHourlyResponse(points);
    }

    // Live proxy, not persisted.
    @GetMapping("/api/solar/status")
    public SolarStatusResponse getSolarStatus() {
        PlantDataDto data = growattService.getLiveStatus();
        if (data == null) {
            return new SolarStatusResponse(null, null, null, null, null, null);
        }
        return new SolarStatusResponse(
                parseBigDecimal(data.today_energy),
                parseBigDecimal(data.monthly_energy),
                parseBigDecimal(data.yearly_energy),
                parseBigDecimal(data.total_energy),
                BigDecimal.valueOf(data.current_power),
                data.last_update_time);
    }

    private String resolvePlantId() {
        if (!growattCredentialsService.hasCredentials()) {
            return null;
        }
        return growattCredentialsService.getCredentials().getPlantId();
    }

    private static PowerPoint toPowerPoint(PowerPointDto dto) {
        return new PowerPoint(dto.time, dto.power != null ? BigDecimal.valueOf(dto.power) : null);
    }

    private static BigDecimal parseBigDecimal(String value) {
        return value != null && !value.isBlank() ? new BigDecimal(value) : null;
    }

    private static List<SolarPeriodEntry> toPeriodEntries(List<SolarByPeriodProjection> rows) {
        return rows.stream().map(r -> new SolarPeriodEntry(r.getPeriod(), r.getKwh())).toList();
    }

    private LocalDate effectiveFromDate(LocalDate fromDate) {
        return fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate effectiveToDate(LocalDate toDate) {
        return toDate != null ? toDate : LocalDate.now().plusDays(1);
    }

    private SolarTotals emptyTotals() {
        return new SolarTotals(0, BigDecimal.ZERO);
    }

    private SolarTotals computeTotalsFromDays(List<SolarDayEntry> days) {
        BigDecimal kwh = days.stream().map(SolarDayEntry::getKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SolarTotals(days.size(), kwh.setScale(4, RoundingMode.HALF_UP));
    }

    private SolarTotals computeTotalsFromPeriods(List<SolarPeriodEntry> periods) {
        BigDecimal kwh = periods.stream().map(SolarPeriodEntry::getKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SolarTotals(periods.size(), kwh.setScale(4, RoundingMode.HALF_UP));
    }

    public static class SolarDayEntry {
        private final LocalDate date;
        private final BigDecimal kwh;

        public SolarDayEntry(LocalDate date, BigDecimal kwh) {
            this.date = date;
            this.kwh = kwh;
        }

        public LocalDate getDate() {
            return date;
        }

        public BigDecimal getKwh() {
            return kwh;
        }
    }

    public static class SolarPeriodEntry {
        private final LocalDate period;
        private final BigDecimal kwh;

        public SolarPeriodEntry(LocalDate period, BigDecimal kwh) {
            this.period = period;
            this.kwh = kwh;
        }

        public LocalDate getPeriod() {
            return period;
        }

        public BigDecimal getKwh() {
            return kwh;
        }
    }

    // Deliberately no cost/rate fields (unlike UsageController.UsageTotals) - solar generation
    // has no tariff concept in this feature. periodCount is the number of rows summed (days for
    // by-day, weeks/months/years for the other granularities), not a fixed unit.
    public static class SolarTotals {
        private final long periodCount;
        private final BigDecimal kwh;

        public SolarTotals(long periodCount, BigDecimal kwh) {
            this.periodCount = periodCount;
            this.kwh = kwh;
        }

        public long getPeriodCount() {
            return periodCount;
        }

        public BigDecimal getKwh() {
            return kwh;
        }
    }

    public static class SolarByDayResponse {
        private final List<SolarDayEntry> days;
        private final SolarTotals totals;

        public SolarByDayResponse(List<SolarDayEntry> days, SolarTotals totals) {
            this.days = days;
            this.totals = totals;
        }

        public List<SolarDayEntry> getDays() {
            return days;
        }

        public SolarTotals getTotals() {
            return totals;
        }
    }

    public static class SolarByWeekResponse {
        private final List<SolarPeriodEntry> weeks;
        private final SolarTotals totals;

        public SolarByWeekResponse(List<SolarPeriodEntry> weeks, SolarTotals totals) {
            this.weeks = weeks;
            this.totals = totals;
        }

        public List<SolarPeriodEntry> getWeeks() {
            return weeks;
        }

        public SolarTotals getTotals() {
            return totals;
        }
    }

    public static class SolarByMonthResponse {
        private final List<SolarPeriodEntry> months;
        private final SolarTotals totals;

        public SolarByMonthResponse(List<SolarPeriodEntry> months, SolarTotals totals) {
            this.months = months;
            this.totals = totals;
        }

        public List<SolarPeriodEntry> getMonths() {
            return months;
        }

        public SolarTotals getTotals() {
            return totals;
        }
    }

    public static class SolarByYearResponse {
        private final List<SolarPeriodEntry> years;
        private final SolarTotals totals;

        public SolarByYearResponse(List<SolarPeriodEntry> years, SolarTotals totals) {
            this.years = years;
            this.totals = totals;
        }

        public List<SolarPeriodEntry> getYears() {
            return years;
        }

        public SolarTotals getTotals() {
            return totals;
        }
    }

    public static class PowerPoint {
        private final String time;
        private final BigDecimal powerWatts;

        public PowerPoint(String time, BigDecimal powerWatts) {
            this.time = time;
            this.powerWatts = powerWatts;
        }

        public String getTime() {
            return time;
        }

        public BigDecimal getPowerWatts() {
            return powerWatts;
        }
    }

    public static class SolarHourlyResponse {
        private final List<PowerPoint> points;

        public SolarHourlyResponse(List<PowerPoint> points) {
            this.points = points;
        }

        public List<PowerPoint> getPoints() {
            return points;
        }
    }

    public static class SolarStatusResponse {
        private final BigDecimal todayKwh;
        private final BigDecimal monthlyKwh;
        private final BigDecimal yearlyKwh;
        private final BigDecimal totalKwh;
        private final BigDecimal currentPowerWatts;
        private final String lastUpdateTime;

        public SolarStatusResponse(BigDecimal todayKwh, BigDecimal monthlyKwh, BigDecimal yearlyKwh,
                                    BigDecimal totalKwh, BigDecimal currentPowerWatts, String lastUpdateTime) {
            this.todayKwh = todayKwh;
            this.monthlyKwh = monthlyKwh;
            this.yearlyKwh = yearlyKwh;
            this.totalKwh = totalKwh;
            this.currentPowerWatts = currentPowerWatts;
            this.lastUpdateTime = lastUpdateTime;
        }

        public BigDecimal getTodayKwh() {
            return todayKwh;
        }

        public BigDecimal getMonthlyKwh() {
            return monthlyKwh;
        }

        public BigDecimal getYearlyKwh() {
            return yearlyKwh;
        }

        public BigDecimal getTotalKwh() {
            return totalKwh;
        }

        public BigDecimal getCurrentPowerWatts() {
            return currentPowerWatts;
        }

        public String getLastUpdateTime() {
            return lastUpdateTime;
        }
    }
}
