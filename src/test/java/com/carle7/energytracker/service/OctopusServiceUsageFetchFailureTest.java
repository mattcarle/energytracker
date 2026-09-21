package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.MeterPointRepository;
import com.carle7.energytracker.repository.MeterRepository;
import com.carle7.energytracker.repository.StandingChargeByDayRepository;
import com.carle7.energytracker.repository.StandingChargeRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import com.carle7.energytracker.repository.UnitRateRepository;
import com.carle7.energytracker.repository.UsageDateRangeProjection;
import com.carle7.energytracker.repository.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// loadUsageData re-fetches from the start of the previous day and replaces that overlap. The
// overlap used to be deleted BEFORE fetching, so a failed fetch (observed live: "Network is
// unreachable" for every meter at once) left the window empty until a later run succeeded. The
// stored usage must now only be replaced once every meter's replacement has actually arrived.
@ExtendWith(MockitoExtension.class)
class OctopusServiceUsageFetchFailureTest {

    @Mock
    private OctopusApiService octopusApiService;

    @Mock
    private UsageRepository usageRepository;

    @Mock
    private MeterRepository meterRepository;

    @Mock
    private MeterPointRepository meterPointRepository;

    @Mock
    private AgreementRepository agreementRepository;

    @Mock
    private StandingChargeRepository standingChargeRepository;

    @Mock
    private UnitRateRepository unitRateRepository;

    @Mock
    private StandingChargeByDayRepository standingChargeByDayRepository;

    @Mock
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @InjectMocks
    private OctopusService octopusService;

    private static final LocalDateTime LATEST = LocalDateTime.parse("2026-01-05T13:30:00");
    private static final LocalDateTime OVERLAP_FROM = LocalDateTime.parse("2026-01-04T00:00:00");

    private static MeterPoint meterPoint(long id, String mpan, String type) {
        MeterPoint mp = new MeterPoint(mpan, false, type);
        mp.setId(id);
        return mp;
    }

    private static UsageDateRangeProjection rangeEndingAt(String mpan, LocalDateTime latest) {
        UsageDateRangeProjection range = mock(UsageDateRangeProjection.class);
        when(range.getMpan()).thenReturn(mpan);
        when(range.getLatest()).thenReturn(latest);
        return range;
    }

    private static OctopusApiService.ConsumptionResponse responseOf(double consumption) {
        OctopusApiService.ConsumptionDto dto = new OctopusApiService.ConsumptionDto();
        dto.consumption = consumption;
        dto.interval_start = "2026-01-05T14:00:00Z";
        dto.interval_end = "2026-01-05T14:30:00Z";
        OctopusApiService.ConsumptionResponse response = new OctopusApiService.ConsumptionResponse();
        response.results = List.of(dto);
        return response;
    }

    private void stubNoUnitRates() {
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());
    }

    @Test
    void failedFetchKeepsStoredUsageAndReportsAnError() {
        MeterPoint elec = meterPoint(1L, "MPAN1", "ELEC");
        UsageDateRangeProjection range = rangeEndingAt("MPAN1", LATEST);
        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of(range));
        when(meterPointRepository.findAll()).thenReturn(List.of(elec));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(new Meter("S1", 1L)));
        when(octopusApiService.fetchConsumptionData(any(), any(), any(), any(), any())).thenReturn(null);
        stubNoUnitRates();

        OctopusService.UsageLoadResult result = octopusService.loadUsageData(false);

        verify(usageRepository, never()).deleteByMpanAndIntervalFromGreaterThanEqual(anyString(), any());
        verify(usageRepository, never()).saveAll(anyList());
        assertThat(result.getUsageCount()).isZero();
        assertThat(result.getError()).contains("1 meter point");
    }

    @Test
    void oneFailedMeterOnAMeterPointKeepsItsWholeStoredWindow() {
        // Readings from several meters share one mpan's stored usage, so replacing the window
        // with only some of them would drop the rest - all or nothing per meter point.
        MeterPoint elec = meterPoint(1L, "MPAN1", "ELEC");
        UsageDateRangeProjection range = rangeEndingAt("MPAN1", LATEST);
        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of(range));
        when(meterPointRepository.findAll()).thenReturn(List.of(elec));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(new Meter("S1", 1L), new Meter("S2", 1L)));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("S1"), any(), any())).thenReturn(responseOf(1.0));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("S2"), any(), any())).thenReturn(null);
        stubNoUnitRates();

        OctopusService.UsageLoadResult result = octopusService.loadUsageData(false);

        verify(usageRepository, never()).deleteByMpanAndIntervalFromGreaterThanEqual(anyString(), any());
        verify(usageRepository, never()).saveAll(anyList());
        assertThat(result.getError()).isNotNull();
    }

    @Test
    void successfulFetchReplacesTheOverlapOnlyAfterTheReplacementHasArrived() {
        MeterPoint elec = meterPoint(1L, "MPAN1", "ELEC");
        UsageDateRangeProjection range = rangeEndingAt("MPAN1", LATEST);
        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of(range));
        when(meterPointRepository.findAll()).thenReturn(List.of(elec));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(new Meter("S1", 1L)));
        when(octopusApiService.fetchConsumptionData(any(), any(), any(), any(), any())).thenReturn(responseOf(1.5));
        when(usageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        stubNoUnitRates();

        OctopusService.UsageLoadResult result = octopusService.loadUsageData(false);

        InOrder order = inOrder(octopusApiService, usageRepository);
        order.verify(octopusApiService).fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("S1"), eq(OVERLAP_FROM), any());
        order.verify(usageRepository).deleteByMpanAndIntervalFromGreaterThanEqual("MPAN1", OVERLAP_FROM);
        order.verify(usageRepository).saveAll(anyList());
        assertThat(result.getUsageCount()).isEqualTo(1);
        assertThat(result.getError()).isNull();
    }

    @Test
    void aFailingMeterPointDoesNotStopTheOthersBeingRefreshed() {
        MeterPoint elec = meterPoint(1L, "MPAN-ELEC", "ELEC");
        MeterPoint gas = meterPoint(2L, "MPAN-GAS", "GAS");
        UsageDateRangeProjection elecRange = rangeEndingAt("MPAN-ELEC", LATEST);
        UsageDateRangeProjection gasRange = rangeEndingAt("MPAN-GAS", LATEST);
        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of(elecRange, gasRange));
        when(meterPointRepository.findAll()).thenReturn(List.of(elec, gas));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(new Meter("SE", 1L)));
        when(meterRepository.findByMeterPointId(2L)).thenReturn(List.of(new Meter("SG", 2L)));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), any(), any(), any(), any())).thenReturn(null);
        when(octopusApiService.fetchConsumptionData(eq("GAS"), any(), any(), any(), any())).thenReturn(responseOf(1.0));
        when(usageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        stubNoUnitRates();

        OctopusService.UsageLoadResult result = octopusService.loadUsageData(false);

        verify(usageRepository, never()).deleteByMpanAndIntervalFromGreaterThanEqual(eq("MPAN-ELEC"), any());
        verify(usageRepository).deleteByMpanAndIntervalFromGreaterThanEqual("MPAN-GAS", OVERLAP_FROM);
        assertThat(result.getUsageCount()).isEqualTo(1);
        assertThat(result.getError()).contains("1 meter point");
    }

    @Test
    void firstEverBackfillHasNothingToDeleteAndStillLoads() {
        MeterPoint elec = meterPoint(1L, "MPAN1", "ELEC");
        var agreement = new com.carle7.energytracker.model.Agreement("TARIFF", LocalDateTime.parse("2026-01-01T00:00:00"), null, 1L);
        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of());
        when(meterPointRepository.findAll()).thenReturn(List.of(elec));
        when(agreementRepository.findByMeterPointIdOrderByValidFrom(1L)).thenReturn(List.of(agreement));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(new Meter("S1", 1L)));
        when(octopusApiService.fetchConsumptionData(any(), any(), any(), any(), any())).thenReturn(responseOf(2.0));
        when(usageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        stubNoUnitRates();

        OctopusService.UsageLoadResult result = octopusService.loadUsageData(false);

        verify(usageRepository, never()).deleteByMpanAndIntervalFromGreaterThanEqual(anyString(), any());
        assertThat(result.getUsageCount()).isEqualTo(1);
        assertThat(result.getError()).isNull();
        verify(usageRepository).saveAll(anyList());
    }
}
