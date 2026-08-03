package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.UsageAggregateProjection;
import com.carle7.energytracker.repository.UsageByDayProjection;
import com.carle7.energytracker.repository.UsageByMonthProjection;
import com.carle7.energytracker.repository.UsageByYearProjection;
import com.carle7.energytracker.repository.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Test
    void byDay_totals_sumAcrossAllDays() {
        List<UsageByDayProjection> days = List.of(
                rowOf(UsageByDayProjection.class, 2, "2.0000", "0.4000"),
                rowOf(UsageByDayProjection.class, 4, "4.0000", "0.9000")
        );
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(days);

        UsageController.UsageByDayResponse response = usageController.getUsageByDay("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null);

        assertThat(response.getDays()).isEqualTo(days);
        assertThat(response.getTotals().getIntervalCount()).isEqualTo(6L);
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("6.0000");
        assertThat(response.getTotals().getCost()).isEqualByComparingTo("1.3000");
        assertThat(response.getTotals().getAvgRate()).isEqualByComparingTo(new BigDecimal("1.3000").divide(new BigDecimal("6.0000"), 6, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void byDay_totals_withNoDays_hasNullAvgRateAndZeroSums() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        UsageController.UsageByDayResponse response = usageController.getUsageByDay("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null);

        assertThat(response.getTotals().getIntervalCount()).isZero();
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotals().getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotals().getAvgRate()).isNull();
    }

    @Test
    void byDay_missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        usageController.getUsageByDay("12345", null, null, null);

        verify(usageRepository).findUsageByDay(
                eq("12345"),
                eq(LocalDate.now().withDayOfMonth(1)),
                eq(LocalDate.now().plusDays(1)),
                eq(List.of("DIRECT_DEBIT", "NA")));
    }

    @Test
    void byDay_explicitParams_arePassedThroughUnchanged() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        usageController.getUsageByDay("12345", LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-15"), List.of("NON_DIRECT_DEBIT"));

        verify(usageRepository).findUsageByDay(
                eq("12345"),
                eq(LocalDate.parse("2026-03-01")),
                eq(LocalDate.parse("2026-03-15")),
                eq(List.of("NON_DIRECT_DEBIT")));
    }

    @Test
    void byMonth_totals_sumAcrossAllMonths() {
        List<UsageByMonthProjection> months = List.of(
                rowOf(UsageByMonthProjection.class, 2, "2.0000", "0.4000"),
                rowOf(UsageByMonthProjection.class, 4, "4.0000", "0.9000")
        );
        when(usageRepository.findUsageByMonth(eq("12345"), any(), any(), anyList())).thenReturn(months);

        UsageController.UsageByMonthResponse response = usageController.getUsageByMonth("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-01"), null);

        assertThat(response.getMonths()).isEqualTo(months);
        assertThat(response.getTotals().getIntervalCount()).isEqualTo(6L);
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("6.0000");
        assertThat(response.getTotals().getCost()).isEqualByComparingTo("1.3000");
    }

    @Test
    void byMonth_missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByMonth(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        usageController.getUsageByMonth("12345", null, null, null);

        verify(usageRepository).findUsageByMonth(
                eq("12345"),
                eq(LocalDate.now().withDayOfMonth(1)),
                eq(LocalDate.now().plusDays(1)),
                eq(List.of("DIRECT_DEBIT", "NA")));
    }

    @Test
    void byYear_totals_sumAcrossAllYears() {
        List<UsageByYearProjection> years = List.of(
                rowOf(UsageByYearProjection.class, 2, "2.0000", "0.4000"),
                rowOf(UsageByYearProjection.class, 4, "4.0000", "0.9000")
        );
        when(usageRepository.findUsageByYear(eq("12345"), any(), any(), anyList())).thenReturn(years);

        UsageController.UsageByYearResponse response = usageController.getUsageByYear("12345", LocalDate.parse("2020-01-01"), LocalDate.parse("2026-07-01"), null);

        assertThat(response.getYears()).isEqualTo(years);
        assertThat(response.getTotals().getIntervalCount()).isEqualTo(6L);
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("6.0000");
        assertThat(response.getTotals().getCost()).isEqualByComparingTo("1.3000");
    }

    @Test
    void byYear_missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByYear(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        usageController.getUsageByYear("12345", null, null, null);

        verify(usageRepository).findUsageByYear(
                eq("12345"),
                eq(LocalDate.now().withDayOfMonth(1)),
                eq(LocalDate.now().plusDays(1)),
                eq(List.of("DIRECT_DEBIT", "NA")));
    }

    private static LocalDate any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
