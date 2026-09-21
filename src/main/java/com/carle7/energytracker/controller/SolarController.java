package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.SolarGeneration;
import com.carle7.energytracker.repository.SolarByPeriodProjection;
import com.carle7.energytracker.repository.SolarDateRangeProjection;
import com.carle7.energytracker.repository.SolarGenerationRepository;
import com.carle7.energytracker.service.GrowattApiService;
import com.carle7.energytracker.service.GrowattApiService.MixDataPointDto;
import com.carle7.energytracker.service.GrowattApiService.PlantDataDto;
import com.carle7.energytracker.service.GrowattCredentialsService;
import com.carle7.energytracker.service.GrowattService;
import com.carle7.energytracker.service.OctopusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
public class SolarController {
    private static final Logger logger = LoggerFactory.getLogger(SolarController.class);


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

        LocalDate today = growattService.today();
        LocalDate effectiveFromDate = effectiveFromDate(fromDate, today);
        LocalDate effectiveToDate = effectiveToDate(toDate, today);

        List<SolarGeneration> rows = solarGenerationRepository
                .findByPlantIdAndGenerationDateGreaterThanEqualAndGenerationDateLessThanOrderByGenerationDateAsc(
                        plantId, effectiveFromDate, storedRangeEnd(effectiveToDate, today));

        List<SolarDayEntry> days = new ArrayList<>(rows.stream()
                .map(r -> new SolarDayEntry(r.getGenerationDate(), r.getEnergyKwh()))
                .toList());
        // Stored rows all end before today, so appending keeps the list in date order.
        BigDecimal todayKwh = liveTodayKwh(effectiveFromDate, effectiveToDate, today);
        if (todayKwh != null) {
            days.add(new SolarDayEntry(today, todayKwh));
        }

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

        LocalDate today = growattService.today();
        LocalDate effectiveFromDate = effectiveFromDate(fromDate, today);
        LocalDate effectiveToDate = effectiveToDate(toDate, today);
        List<SolarPeriodEntry> weeks = toPeriodEntries(
                solarGenerationRepository.findByWeek(plantId, effectiveFromDate, storedRangeEnd(effectiveToDate, today)));
        weeks = withLiveToday(weeks, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                liveTodayKwh(effectiveFromDate, effectiveToDate, today));
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

        LocalDate today = growattService.today();
        LocalDate effectiveFromDate = effectiveFromDate(fromDate, today);
        LocalDate effectiveToDate = effectiveToDate(toDate, today);
        List<SolarPeriodEntry> months = toPeriodEntries(
                solarGenerationRepository.findByMonth(plantId, effectiveFromDate, storedRangeEnd(effectiveToDate, today)));
        months = withLiveToday(months, today.withDayOfMonth(1), liveTodayKwh(effectiveFromDate, effectiveToDate, today));
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

        LocalDate today = growattService.today();
        LocalDate effectiveFromDate = effectiveFromDate(fromDate, today);
        LocalDate effectiveToDate = effectiveToDate(toDate, today);
        List<SolarPeriodEntry> years = toPeriodEntries(
                solarGenerationRepository.findByYear(plantId, effectiveFromDate, storedRangeEnd(effectiveToDate, today)));
        years = withLiveToday(years, today.withDayOfYear(1), liveTodayKwh(effectiveFromDate, effectiveToDate, today));
        return new SolarByYearResponse(years, computeTotalsFromPeriods(years));
    }

    // Live proxy, not persisted - see the plan's note on why intraday power isn't backfilled.
    // Device-level (mix_data's `ppv`), not the plant-level power endpoint - see
    // GrowattApiService.fetchMixData's comment for why: the plant-level figure turned out to be
    // inverter AC output (mixed with battery activity), not isolated PV.
    @GetMapping("/api/solar/hourly")
    public SolarHourlyResponse getSolarHourly(@RequestParam LocalDate date) {
        List<MixDataPointDto> data = growattService.getLivePowerCurve(date).points;
        if (data == null) {
            return new SolarHourlyResponse(List.of());
        }
        // Points come back in arbitrary order, not chronological - time is
        // "yyyy-MM-dd HH:mm:ss" (fixed-width, zero-padded), so plain string ordering sorts it
        // correctly without needing a date-time parse.
        List<PowerPoint> points = data.stream()
                .sorted(Comparator.comparing(dto -> dto.time))
                .map(SolarController::toPowerPoint)
                .toList();
        return new SolarHourlyResponse(points);
    }

    // Live proxy, not persisted.
    @GetMapping("/api/solar/status")
    public SolarStatusResponse getSolarStatus() {
        logger.info("Fetching solar status data");
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

    // Live proxy for the Live tab, not persisted - the most recent of today's mix_data points
    // (Growatt's own ~5-minute reporting cadence, same source/granularity getSolarHourly's curve
    // is built from - there's no separate lower-latency "instant status" call in the V1 API this
    // app uses). gridWatts/batteryWatts follow the same sign convention as import/export usage
    // elsewhere in this app (see useUsagePeriodData on the frontend): positive means energy
    // flowing in (importing from grid / charging the battery), negative means flowing out
    // (exporting to grid / discharging the battery).
    @GetMapping("/api/solar/live")
    public SolarLiveResponse getSolarLive() {
        logger.info("Fetching solar live data");

        GrowattApiService.MixDataResult result = growattService.getLivePowerCurve(growattService.today());
        List<MixDataPointDto> data = result.points;
        // Time is "yyyy-MM-dd HH:mm:ss", fixed-width - see getSolarHourly's own comment on why
        // plain string comparison both sorts and finds-the-max correctly here.
        MixDataPointDto latest = data == null ? null : data.stream().max(Comparator.comparing(dto -> dto.time)).orElse(null);
        if (latest == null) {
            // result.error is null (not shown as an error) when Growatt legitimately has
            // nothing to report yet (e.g. before today's first reading) - only a genuine
            // failure (credentials/device problem, HTTP/parse error, or Growatt's own
            // error_code) populates it. See GrowattApiService.MixDataResult.
            return new SolarLiveResponse(null, null, null, null, null, null, null, List.of(), result.error);
        }
        // The whole day's curve rides along with the latest reading because this call has already
        // fetched every one of today's points from Growatt to find it - the Live tab's chart
        // reuses them rather than making a second identical mix_data request (see getSolarHourly).
        List<PowerPoint> curve = data.stream()
                .sorted(Comparator.comparing(dto -> dto.time))
                .map(SolarController::toPowerPoint)
                .toList();
        return new SolarLiveResponse(
                toWatts(latest.ppv),
                signedWatts(latest.pacToUserTotal, latest.pacToGridTotal),
                toWatts(latest.plocalLoadTotal),
                signedWatts(latest.pcharge1, latest.pdischarge1),
                latest.soc,
                latest.epvtoday != null ? BigDecimal.valueOf(latest.epvtoday) : null,
                latest.time,
                curve,
                null);
    }

    private static BigDecimal toWatts(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    private static BigDecimal signedWatts(Double positive, Double negative) {
        if (positive == null && negative == null) {
            return null;
        }
        double p = positive != null ? positive : 0;
        double n = negative != null ? negative : 0;
        return BigDecimal.valueOf(p - n);
    }

    private String resolvePlantId() {
        if (!growattCredentialsService.hasCredentials()) {
            return null;
        }
        return growattCredentialsService.getCredentials().getPlantId();
    }

    private static PowerPoint toPowerPoint(MixDataPointDto dto) {
        return new PowerPoint(
                dto.time,
                dto.ppv != null ? BigDecimal.valueOf(dto.ppv) : null,
                dto.soc,
                dto.plocalLoadTotal != null ? BigDecimal.valueOf(dto.plocalLoadTotal) : null);
    }

    private static BigDecimal parseBigDecimal(String value) {
        return value != null && !value.isBlank() ? new BigDecimal(value) : null;
    }

    private static List<SolarPeriodEntry> toPeriodEntries(List<SolarByPeriodProjection> rows) {
        return rows.stream().map(r -> new SolarPeriodEntry(r.getPeriod(), r.getKwh())).toList();
    }

    private LocalDate effectiveFromDate(LocalDate fromDate, LocalDate today) {
        return fromDate != null ? fromDate : today.withDayOfMonth(1);
    }

    private LocalDate effectiveToDate(LocalDate toDate, LocalDate today) {
        return toDate != null ? toDate : today.plusDays(1);
    }

    // solar_generation never holds today (the backfill stops at yesterday - see
    // GrowattService.loadSolarData), so stored rows are only read up to today, exclusive; today's
    // running total comes live from Growatt instead (see liveTodayKwh). Cutting the stored query
    // off at today also means a stray stored row for today can't be double counted.
    private static LocalDate storedRangeEnd(LocalDate toDate, LocalDate today) {
        return toDate.isAfter(today) ? today : toDate;
    }

    // Today's generation so far, or null when today isn't inside [fromDate, toDate) - so Growatt
    // is only called for pages that actually show today - or when Growatt can't supply it, in
    // which case the result is just today left out, as it was before this was added.
    private BigDecimal liveTodayKwh(LocalDate fromDate, LocalDate toDate, LocalDate today) {
        if (today.isBefore(fromDate) || !today.isBefore(toDate)) {
            return null;
        }
        return growattService.getTodayKwh();
    }

    // Adds today's kWh to the period entry that contains it (periodStart is that period's start
    // date - the same key the stored by-week/by-month/by-year queries group on), inserting a new
    // entry, in period order, when none of the stored rows fall in that period yet.
    private static List<SolarPeriodEntry> withLiveToday(List<SolarPeriodEntry> stored, LocalDate periodStart, BigDecimal todayKwh) {
        if (todayKwh == null) {
            return stored;
        }
        List<SolarPeriodEntry> result = new ArrayList<>();
        boolean merged = false;
        for (SolarPeriodEntry entry : stored) {
            if (entry.getPeriod().equals(periodStart)) {
                result.add(new SolarPeriodEntry(periodStart, entry.getKwh().add(todayKwh)));
                merged = true;
            } else {
                result.add(entry);
            }
        }
        if (!merged) {
            result.add(new SolarPeriodEntry(periodStart, todayKwh));
            result.sort(Comparator.comparing(SolarPeriodEntry::getPeriod));
        }
        return result;
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
        // Battery state of charge, 0-100. Null when the device didn't report it for this point.
        private final Integer batteryPercent;
        // House load consumption power, Watts. Null when the device didn't report it.
        private final BigDecimal loadWatts;

        public PowerPoint(String time, BigDecimal powerWatts, Integer batteryPercent, BigDecimal loadWatts) {
            this.time = time;
            this.powerWatts = powerWatts;
            this.batteryPercent = batteryPercent;
            this.loadWatts = loadWatts;
        }

        public String getTime() {
            return time;
        }

        public BigDecimal getPowerWatts() {
            return powerWatts;
        }

        public Integer getBatteryPercent() {
            return batteryPercent;
        }

        public BigDecimal getLoadWatts() {
            return loadWatts;
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

    public static class SolarLiveResponse {
        private final BigDecimal solarWatts;
        private final BigDecimal gridWatts;
        private final BigDecimal loadWatts;
        private final BigDecimal batteryWatts;
        private final Integer batterySoc;
        private final BigDecimal solarTodayKwh;
        private final String time;
        // Every reading Growatt has reported so far today, oldest first (empty when there are
        // none yet) - the same shape getSolarHourly returns, for the Live tab's chart.
        private final List<PowerPoint> points;
        // Set only on a genuine Growatt API failure (see GrowattApiService.MixDataResult) - null
        // otherwise, including when Growatt simply has nothing to report yet.
        private final String error;

        public SolarLiveResponse(BigDecimal solarWatts, BigDecimal gridWatts, BigDecimal loadWatts,
                                  BigDecimal batteryWatts, Integer batterySoc, BigDecimal solarTodayKwh, String time,
                                  List<PowerPoint> points, String error) {
            this.solarWatts = solarWatts;
            this.gridWatts = gridWatts;
            this.loadWatts = loadWatts;
            this.batteryWatts = batteryWatts;
            this.batterySoc = batterySoc;
            this.solarTodayKwh = solarTodayKwh;
            this.time = time;
            this.points = points;
            this.error = error;
        }

        public BigDecimal getSolarWatts() {
            return solarWatts;
        }

        public BigDecimal getGridWatts() {
            return gridWatts;
        }

        public BigDecimal getLoadWatts() {
            return loadWatts;
        }

        public BigDecimal getBatteryWatts() {
            return batteryWatts;
        }

        public Integer getBatterySoc() {
            return batterySoc;
        }

        public BigDecimal getSolarTodayKwh() {
            return solarTodayKwh;
        }

        public String getTime() {
            return time;
        }

        public List<PowerPoint> getPoints() {
            return points;
        }

        public String getError() {
            return error;
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
