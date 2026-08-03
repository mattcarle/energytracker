package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.UsageByDayProjection;
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

    private UsageByDayProjection dayOf(long intervalCount, String kwh, String cost) {
        UsageByDayProjection day = mock(UsageByDayProjection.class);
        when(day.getIntervalCount()).thenReturn(intervalCount);
        when(day.getKwh()).thenReturn(new BigDecimal(kwh));
        when(day.getCost()).thenReturn(new BigDecimal(cost));
        return day;
    }

    @Test
    void totals_sumAcrossAllDays() {
        List<UsageByDayProjection> days = List.of(
                dayOf(2, "2.0000", "0.4000"),
                dayOf(4, "4.0000", "0.9000")
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
    void totals_withNoDays_hasNullAvgRateAndZeroSums() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        UsageController.UsageByDayResponse response = usageController.getUsageByDay("12345", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null);

        assertThat(response.getTotals().getIntervalCount()).isZero();
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotals().getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotals().getAvgRate()).isNull();
    }

    @Test
    void missingFromToAndPaymentMethods_useDefaults() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        usageController.getUsageByDay("12345", null, null, null);

        verify(usageRepository).findUsageByDay(
                eq("12345"),
                eq(LocalDate.now().withDayOfMonth(1)),
                eq(LocalDate.now().plusDays(1)),
                eq(List.of("DIRECT_DEBIT", "NA")));
    }

    @Test
    void explicitParams_arePassedThroughUnchanged() {
        when(usageRepository.findUsageByDay(eq("12345"), any(), any(), anyList())).thenReturn(List.of());

        usageController.getUsageByDay("12345", LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-15"), List.of("NON_DIRECT_DEBIT"));

        verify(usageRepository).findUsageByDay(
                eq("12345"),
                eq(LocalDate.parse("2026-03-01")),
                eq(LocalDate.parse("2026-03-15")),
                eq(List.of("NON_DIRECT_DEBIT")));
    }

    private static LocalDate any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
