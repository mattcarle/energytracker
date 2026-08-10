package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.RateBreakdown;
import com.carle7.energytracker.repository.UsageAggregateProjection;
import com.carle7.energytracker.repository.UsageByDayGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByDayProjection;
import com.carle7.energytracker.repository.UsageByMonthGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByMonthProjection;
import com.carle7.energytracker.repository.UsageByYearGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByYearProjection;
import com.carle7.energytracker.repository.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageControllerTest {

    @Mock
    private UsageRepository usageRepository;

    @InjectMocks
    private UsageController usageController;

    private <T extends UsageAggregateProjection> T rowOf(Class<T> type, long intervalCount, String kwh, String cost) {
        T row = mock(type);
        when(row.getIntervalCount()).thenReturn(intervalCount);
        when(row.getKwh()).thenReturn(new BigDecimal(kwh));
        when(row.getCost()).thenReturn(new BigDecimal(cost));
        return row;
    }

    private UsageByDayGroupByRateAndRateTypeProjection dayBreakdownRowOf(LocalDate usageDate, String rateType, String rate, String kwh) {
        UsageByDayGroupByRateAndRateTypeProjection row = mock(UsageByDayGroupByRateAndRateTypeProjection.class);
        when(row.getUsageDate()).thenReturn(usageDate);
        when(row.getRateType()).thenReturn(rateType);
        when(row.getRate()).thenReturn(new BigDecimal(rate));
        when(row.getKwh()).thenReturn(new BigDecimal(kwh));
        return row;
    }

    private UsageByMonthGroupByRateAndRateTypeProjection monthBreakdownRowOf(LocalDate usageMonth, String rateType, String rate, String kwh) {
        UsageByMonthGroupByRateAndRateTypeProjection row = mock(UsageByMonthGroupByRateAndRateTypeProjection.class);
        when(row.getUsageMonth()).thenReturn(usageMonth);
        when(row.getRateType()).thenReturn(rateType);
        when(row.getRate()).thenReturn(new BigDecimal(rate));
        when(row.getKwh()).thenReturn(new BigDecimal(kwh));
        return row;
    }

    private UsageByYearGroupByRateAndRateTypeProjection yearBreakdownRowOf(LocalDate usageYear, String rateType, String rate, String kwh) {
        UsageByYearGroupByRateAndRateTypeProjection row = mock(UsageByYearGroupByRateAndRateTypeProjection.class);
        when(row.getUsageYear()).thenReturn(usageYear);
        when(row.getRateType()).thenReturn(rateType);
        when(row.getRate()).thenReturn(new BigDecimal(rate));
        when(row.getKwh()).thenReturn(new BigDecimal(kwh));
        return row;
    }

    private void assertBreakdownEntry(RateBreakdown entry, String rateType, String rate, String kwh) {
        assertThat(entry.getRateType()).isEqualTo(rateType);
        assertThat(entry.getRate()).isEqualByComparingTo(rate);
        assertThat(entry.getKwh()).isEqualByComparingTo(kwh);
    }

    @Test
    void byDay_totals_sumAcrossAllDays() {
        List<UsageByDayProjection> days = List.of(
                rowOf(UsageByDayProjection.class, 2, "2.0000", "0.4000"),
                rowOf(UsageByDayProjection.class, 4, "4.0000", "0.9000")
        );
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(days);
        when(usageRepository.findUsageByDayGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        UsageController.UsageByDayResponse response = usageController.getUsageByDay("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null);

        assertThat(response.getDays()).extracting(UsageByDayProjection::getKwh)
                .containsExactly(new BigDecimal("2.0000"), new BigDecimal("4.0000"));
        assertThat(response.getDays()).allMatch(day -> day.getBreakdown().isEmpty());
        assertThat(response.getTotals().getIntervalCount()).isEqualTo(6L);
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("6.0000");
        assertThat(response.getTotals().getCost()).isEqualByComparingTo("1.3000");
        assertThat(response.getTotals().getAvgRate()).isEqualByComparingTo(new BigDecimal("1.3000").divide(new BigDecimal("6.0000"), 6, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void byDay_breakdown_isAttachedPerDayMatchedByUsageDate() {
        UsageByDayProjection day1 = rowOf(UsageByDayProjection.class, 2, "2.0000", "0.4000");
        when(day1.getUsageDate()).thenReturn(LocalDate.parse("2026-01-05"));
        UsageByDayProjection day2 = rowOf(UsageByDayProjection.class, 4, "4.0000", "0.9000");
        when(day2.getUsageDate()).thenReturn(LocalDate.parse("2026-01-06"));
        List<UsageByDayProjection> days = List.of(day1, day2);
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(days);

        UsageByDayGroupByRateAndRateTypeProjection day1Night = dayBreakdownRowOf(LocalDate.parse("2026-01-05"), "NIGHT", "0.07", "1.5000");
        UsageByDayGroupByRateAndRateTypeProjection day1Day = dayBreakdownRowOf(LocalDate.parse("2026-01-05"), "DAY", "0.25", "0.5000");
        when(usageRepository.findUsageByDayGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime()))
                .thenReturn(List.of(day1Night, day1Day));

        UsageController.UsageByDayResponse response = usageController.getUsageByDay("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null);

        assertThat(response.getDays()).hasSize(2);
        UsageByDayProjection responseDay1 = response.getDays().get(0);
        assertThat(responseDay1.getUsageDate()).isEqualTo(LocalDate.parse("2026-01-05"));
        assertThat(responseDay1.getKwh()).isEqualByComparingTo("2.0000");
        List<RateBreakdown> day1Breakdown = responseDay1.getBreakdown();
        assertThat(day1Breakdown).hasSize(2);
        assertBreakdownEntry(day1Breakdown.get(0), "NIGHT", "0.07", "1.5000");
        assertBreakdownEntry(day1Breakdown.get(1), "DAY", "0.25", "0.5000");

        // day2 has no matching breakdown rows for its date, so it gets an empty breakdown rather than day1's.
        UsageByDayProjection responseDay2 = response.getDays().get(1);
        assertThat(responseDay2.getUsageDate()).isEqualTo(LocalDate.parse("2026-01-06"));
        assertThat(responseDay2.getBreakdown()).isEmpty();
    }

    @Test
    void byDay_totals_withNoDays_hasNullAvgRateAndZeroSums() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());
        when(usageRepository.findUsageByDayGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        UsageController.UsageByDayResponse response = usageController.getUsageByDay("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null);

        assertThat(response.getDays()).isEmpty();
        assertThat(response.getTotals().getIntervalCount()).isZero();
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotals().getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotals().getAvgRate()).isNull();
    }

    @Test
    void byDay_missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());
        when(usageRepository.findUsageByDayGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        usageController.getUsageByDay("12345", null, null, null);

        LocalDate defaultFromDate = LocalDate.now().withDayOfMonth(1);
        LocalDate defaultToDate = LocalDate.now().plusDays(1);

        verify(usageRepository).findUsageByDay(
                eq("12345"),
                eq(defaultFromDate),
                eq(defaultToDate),
                eq(List.of("DIRECT_DEBIT", "NA")));

        verify(usageRepository).findUsageByDayGroupByRateAndRateType(
                eq("12345"),
                eq(defaultFromDate.atStartOfDay()),
                eq(defaultToDate.atStartOfDay()));
    }

    @Test
    void byDay_explicitParams_arePassedThroughUnchanged() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());
        when(usageRepository.findUsageByDayGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        usageController.getUsageByDay("12345", LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-15"), List.of("NON_DIRECT_DEBIT"));

        verify(usageRepository).findUsageByDay(
                eq("12345"),
                eq(LocalDate.parse("2026-03-01")),
                eq(LocalDate.parse("2026-03-15")),
                eq(List.of("NON_DIRECT_DEBIT")));

        verify(usageRepository).findUsageByDayGroupByRateAndRateType(
                eq("12345"),
                eq(LocalDateTime.parse("2026-03-01T00:00:00")),
                eq(LocalDateTime.parse("2026-03-15T00:00:00")));
    }

    @Test
    void byMonth_totals_sumAcrossAllMonths() {
        List<UsageByMonthProjection> months = List.of(
                rowOf(UsageByMonthProjection.class, 2, "2.0000", "0.4000"),
                rowOf(UsageByMonthProjection.class, 4, "4.0000", "0.9000")
        );
        when(usageRepository.findUsageByMonth(eq("12345"), any(), any(), anyList())).thenReturn(months);
        when(usageRepository.findUsageByMonthGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        UsageController.UsageByMonthResponse response = usageController.getUsageByMonth("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-01"), null);

        assertThat(response.getMonths()).extracting(UsageByMonthProjection::getKwh)
                .containsExactly(new BigDecimal("2.0000"), new BigDecimal("4.0000"));
        assertThat(response.getTotals().getIntervalCount()).isEqualTo(6L);
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("6.0000");
        assertThat(response.getTotals().getCost()).isEqualByComparingTo("1.3000");
    }

    @Test
    void byMonth_breakdown_matchesRateTypeAndRateForJuly2026() {
        // Total usage for July 2026 is 750 kWh: 550 kWh at the NIGHT rate (0.07) and 200 kWh at the DAY rate (0.25).
        UsageByMonthProjection july = rowOf(UsageByMonthProjection.class, 1488, "750.0000", "162.5000");
        when(july.getUsageMonth()).thenReturn(LocalDate.parse("2026-07-01"));
        List<UsageByMonthProjection> months = List.of(july);
        when(usageRepository.findUsageByMonth(eq("12345"), any(), any(), anyList())).thenReturn(months);

        UsageByMonthGroupByRateAndRateTypeProjection nightBreakdown = monthBreakdownRowOf(LocalDate.parse("2026-07-01"), "NIGHT", "0.07", "550.0000");
        UsageByMonthGroupByRateAndRateTypeProjection dayBreakdown = monthBreakdownRowOf(LocalDate.parse("2026-07-01"), "DAY", "0.25", "200.0000");
        when(usageRepository.findUsageByMonthGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime()))
                .thenReturn(List.of(nightBreakdown, dayBreakdown));

        UsageController.UsageByMonthResponse response = usageController.getUsageByMonth("12345", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"), null);

        assertThat(response.getMonths()).hasSize(1);
        UsageByMonthProjection julyUsage = response.getMonths().get(0);
        assertThat(julyUsage.getUsageMonth()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(julyUsage.getKwh()).isEqualByComparingTo("750.0000");

        List<RateBreakdown> breakdown = julyUsage.getBreakdown();
        assertThat(breakdown).hasSize(2);
        assertBreakdownEntry(breakdown.get(0), "NIGHT", "0.07", "550.0000");
        assertBreakdownEntry(breakdown.get(1), "DAY", "0.25", "200.0000");

        BigDecimal breakdownKwhTotal = breakdown.stream()
                .map(RateBreakdown::getKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(breakdownKwhTotal).isEqualByComparingTo("750.0000");
        assertThat(julyUsage.getKwh()).isEqualByComparingTo(breakdownKwhTotal);
    }

    @Test
    void byMonth_missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByMonth(eq("12345"), any(), any(), anyList())).thenReturn(List.of());
        when(usageRepository.findUsageByMonthGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        usageController.getUsageByMonth("12345", null, null, null);

        LocalDate defaultFromDate = LocalDate.now().withDayOfMonth(1);
        LocalDate defaultToDate = LocalDate.now().plusDays(1);

        verify(usageRepository).findUsageByMonth(
                eq("12345"),
                eq(defaultFromDate),
                eq(defaultToDate),
                eq(List.of("DIRECT_DEBIT", "NA")));

        verify(usageRepository).findUsageByMonthGroupByRateAndRateType(
                eq("12345"),
                eq(defaultFromDate.atStartOfDay()),
                eq(defaultToDate.atStartOfDay()));
    }

    @Test
    void byYear_totals_sumAcrossAllYears() {
        List<UsageByYearProjection> years = List.of(
                rowOf(UsageByYearProjection.class, 2, "2.0000", "0.4000"),
                rowOf(UsageByYearProjection.class, 4, "4.0000", "0.9000")
        );
        when(usageRepository.findUsageByYear(eq("12345"), any(), any(), anyList())).thenReturn(years);
        when(usageRepository.findUsageByYearGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        UsageController.UsageByYearResponse response = usageController.getUsageByYear("12345", LocalDate.parse("2020-01-01"), LocalDate.parse("2026-07-01"), null);

        assertThat(response.getYears()).extracting(UsageByYearProjection::getKwh)
                .containsExactly(new BigDecimal("2.0000"), new BigDecimal("4.0000"));
        assertThat(response.getTotals().getIntervalCount()).isEqualTo(6L);
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("6.0000");
        assertThat(response.getTotals().getCost()).isEqualByComparingTo("1.3000");
    }

    @Test
    void byYear_breakdown_isAttachedPerYearMatchedByUsageYear() {
        UsageByYearProjection year2026 = rowOf(UsageByYearProjection.class, 100, "1000.0000", "150.0000");
        when(year2026.getUsageYear()).thenReturn(LocalDate.parse("2026-01-01"));
        List<UsageByYearProjection> years = List.of(year2026);
        when(usageRepository.findUsageByYear(eq("12345"), any(), any(), anyList())).thenReturn(years);

        UsageByYearGroupByRateAndRateTypeProjection nightBreakdown = yearBreakdownRowOf(LocalDate.parse("2026-01-01"), "NIGHT", "0.07", "700.0000");
        UsageByYearGroupByRateAndRateTypeProjection dayBreakdown = yearBreakdownRowOf(LocalDate.parse("2026-01-01"), "DAY", "0.25", "300.0000");
        when(usageRepository.findUsageByYearGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime()))
                .thenReturn(List.of(nightBreakdown, dayBreakdown));

        UsageController.UsageByYearResponse response = usageController.getUsageByYear("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"), null);

        assertThat(response.getYears()).hasSize(1);
        UsageByYearProjection yearUsage = response.getYears().get(0);
        assertThat(yearUsage.getUsageYear()).isEqualTo(LocalDate.parse("2026-01-01"));

        List<RateBreakdown> breakdown = yearUsage.getBreakdown();
        assertThat(breakdown).hasSize(2);
        assertBreakdownEntry(breakdown.get(0), "NIGHT", "0.07", "700.0000");
        assertBreakdownEntry(breakdown.get(1), "DAY", "0.25", "300.0000");
    }

    @Test
    void byYear_missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByYear(eq("12345"), any(), any(), anyList())).thenReturn(List.of());
        when(usageRepository.findUsageByYearGroupByRateAndRateType(eq("12345"), anyDateTime(), anyDateTime())).thenReturn(List.of());

        usageController.getUsageByYear("12345", null, null, null);

        LocalDate defaultFromDate = LocalDate.now().withDayOfMonth(1);
        LocalDate defaultToDate = LocalDate.now().plusDays(1);

        verify(usageRepository).findUsageByYear(
                eq("12345"),
                eq(defaultFromDate),
                eq(defaultToDate),
                eq(List.of("DIRECT_DEBIT", "NA")));

        verify(usageRepository).findUsageByYearGroupByRateAndRateType(
                eq("12345"),
                eq(defaultFromDate.atStartOfDay()),
                eq(defaultToDate.atStartOfDay()));
    }

    private static LocalDate any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static LocalDateTime anyDateTime() {
        return org.mockito.ArgumentMatchers.any();
    }
}
