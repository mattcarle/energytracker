package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.GrowattCredentials;
import com.carle7.energytracker.model.SolarGeneration;
import com.carle7.energytracker.repository.SolarByPeriodProjection;
import com.carle7.energytracker.repository.SolarGenerationRepository;
import com.carle7.energytracker.service.GrowattCredentialsService;
import com.carle7.energytracker.service.GrowattService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The usage pages' solar figures come from stored daily totals, which stop at yesterday (the
// Growatt backfill never fetches a day that isn't finished - see GrowattService.loadSolarData).
// These check that today's running total, fetched live, is added on so a page covering today
// includes it: e.g. 250 kWh stored for 1-20 Sept plus 10 kWh so far on the 21st is 260 for Sept.
@ExtendWith(MockitoExtension.class)
class SolarControllerTodayTest {

    private static final String PLANT_ID = "12345";
    // A Monday, so "start of this week" is today itself.
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private SolarGenerationRepository solarGenerationRepository;

    @Mock
    private GrowattCredentialsService growattCredentialsService;

    @Mock
    private GrowattService growattService;

    @InjectMocks
    private SolarController solarController;

    @BeforeEach
    void setUp() {
        // lenient: shared setup, and the no-credentials test returns before using most of it.
        GrowattCredentials credentials = mock(GrowattCredentials.class);
        lenient().when(credentials.getPlantId()).thenReturn(PLANT_ID);
        lenient().when(growattCredentialsService.hasCredentials()).thenReturn(true);
        lenient().when(growattCredentialsService.getCredentials()).thenReturn(credentials);
        lenient().when(growattService.today()).thenReturn(TODAY);
    }

    // A real implementation rather than a mock: rows get built inside other when(...).thenReturn(...)
    // calls, and Mockito can't stub one mock while another stubbing is still in progress.
    private record PeriodRow(LocalDate period, BigDecimal kwh) implements SolarByPeriodProjection {
        @Override
        public LocalDate getPeriod() {
            return period;
        }

        @Override
        public BigDecimal getKwh() {
            return kwh;
        }
    }

    private static SolarByPeriodProjection periodRow(LocalDate period, String kwh) {
        return new PeriodRow(period, new BigDecimal(kwh));
    }

    @Test
    void monthPageIncludesTodaysSolarInTheMonthTotal() {
        LocalDate september = LocalDate.of(2026, 9, 1);
        when(solarGenerationRepository.findByMonth(PLANT_ID, september, TODAY))
                .thenReturn(List.of(periodRow(september, "250")));
        when(growattService.getTodayKwh()).thenReturn(new BigDecimal("10"));

        SolarController.SolarByMonthResponse response =
                solarController.getSolarByMonth(september, LocalDate.of(2026, 10, 1));

        assertThat(response.getMonths()).hasSize(1);
        assertThat(response.getMonths().get(0).getKwh()).isEqualByComparingTo("260");
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("260");
    }

    @Test
    void dayListGetsATodayEntryAndStoredRowsStopBeforeToday() {
        LocalDate from = LocalDate.of(2026, 9, 20);
        when(solarGenerationRepository
                .findByPlantIdAndGenerationDateGreaterThanEqualAndGenerationDateLessThanOrderByGenerationDateAsc(
                        PLANT_ID, from, TODAY))
                .thenReturn(List.of(new SolarGeneration(PLANT_ID, from, new BigDecimal("12.5"))));
        when(growattService.getTodayKwh()).thenReturn(new BigDecimal("10"));

        SolarController.SolarByDayResponse response = solarController.getSolarByDay(from, TODAY.plusDays(1));

        assertThat(response.getDays()).extracting(SolarController.SolarDayEntry::getDate)
                .containsExactly(from, TODAY);
        assertThat(response.getDays().get(1).getKwh()).isEqualByComparingTo("10");
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("22.5");
    }

    @Test
    void dayPageForTodayShowsTodaysTotalEvenThoughNothingIsStoredForIt() {
        when(solarGenerationRepository
                .findByPlantIdAndGenerationDateGreaterThanEqualAndGenerationDateLessThanOrderByGenerationDateAsc(
                        PLANT_ID, TODAY, TODAY))
                .thenReturn(List.of());
        when(growattService.getTodayKwh()).thenReturn(new BigDecimal("10"));

        SolarController.SolarByDayResponse response = solarController.getSolarByDay(TODAY, TODAY.plusDays(1));

        assertThat(response.getDays()).hasSize(1);
        assertThat(response.getDays().get(0).getDate()).isEqualTo(TODAY);
        assertThat(response.getDays().get(0).getKwh()).isEqualByComparingTo("10");
    }

    @Test
    void weekWithNoStoredRowsYetGetsANewEntryForTheCurrentWeek() {
        when(solarGenerationRepository.findByWeek(PLANT_ID, TODAY, TODAY)).thenReturn(List.of());
        when(growattService.getTodayKwh()).thenReturn(new BigDecimal("10"));

        SolarController.SolarByWeekResponse response = solarController.getSolarByWeek(TODAY, TODAY.plusDays(7));

        assertThat(response.getWeeks()).hasSize(1);
        assertThat(response.getWeeks().get(0).getPeriod()).isEqualTo(TODAY);
        assertThat(response.getWeeks().get(0).getKwh()).isEqualByComparingTo("10");
    }

    @Test
    void midWeekTodayIsAddedToTheWeekStartingOnThatMonday() {
        // Wednesday 23 Sept; its week starts Monday 21 Sept.
        LocalDate wednesday = LocalDate.of(2026, 9, 23);
        LocalDate lastWeek = LocalDate.of(2026, 9, 14);
        LocalDate thisWeek = LocalDate.of(2026, 9, 21);
        when(growattService.today()).thenReturn(wednesday);
        when(solarGenerationRepository.findByWeek(PLANT_ID, lastWeek, wednesday))
                .thenReturn(List.of(periodRow(lastWeek, "40"), periodRow(thisWeek, "25")));
        when(growattService.getTodayKwh()).thenReturn(new BigDecimal("5"));

        SolarController.SolarByWeekResponse response = solarController.getSolarByWeek(lastWeek, thisWeek.plusDays(7));

        assertThat(response.getWeeks()).extracting(SolarController.SolarPeriodEntry::getPeriod)
                .containsExactly(lastWeek, thisWeek);
        assertThat(response.getWeeks().get(1).getKwh()).isEqualByComparingTo("30");
    }

    @Test
    void yearPageIncludesTodaysSolar() {
        LocalDate year = LocalDate.of(2026, 1, 1);
        when(solarGenerationRepository.findByYear(PLANT_ID, year, TODAY))
                .thenReturn(List.of(periodRow(year, "1000")));
        when(growattService.getTodayKwh()).thenReturn(new BigDecimal("10"));

        SolarController.SolarByYearResponse response = solarController.getSolarByYear(year, LocalDate.of(2027, 1, 1));

        assertThat(response.getYears()).hasSize(1);
        assertThat(response.getYears().get(0).getKwh()).isEqualByComparingTo("1010");
    }

    @Test
    void rangeThatEndsBeforeTodayDoesNotCallGrowatt() {
        LocalDate august = LocalDate.of(2026, 8, 1);
        LocalDate september = LocalDate.of(2026, 9, 1);
        when(solarGenerationRepository.findByMonth(PLANT_ID, august, september))
                .thenReturn(List.of(periodRow(august, "300")));

        SolarController.SolarByMonthResponse response = solarController.getSolarByMonth(august, september);

        verify(growattService, never()).getTodayKwh();
        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("300");
    }

    @Test
    void storedTotalsAreReturnedUnchangedWhenGrowattCannotSupplyToday() {
        LocalDate september = LocalDate.of(2026, 9, 1);
        when(solarGenerationRepository.findByMonth(PLANT_ID, september, TODAY))
                .thenReturn(List.of(periodRow(september, "250")));
        when(growattService.getTodayKwh()).thenReturn(null);

        SolarController.SolarByMonthResponse response =
                solarController.getSolarByMonth(september, LocalDate.of(2026, 10, 1));

        assertThat(response.getTotals().getKwh()).isEqualByComparingTo("250");
    }

    @Test
    void noGrowattCredentialsMeansNoSolarAndNoGrowattCall() {
        when(growattCredentialsService.hasCredentials()).thenReturn(false);

        SolarController.SolarByMonthResponse response =
                solarController.getSolarByMonth(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

        assertThat(response.getMonths()).isEmpty();
        verify(growattService, never()).getTodayKwh();
        verify(solarGenerationRepository, never()).findByMonth(any(), any(), any());
    }
}
