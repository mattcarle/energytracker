package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Agreement;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OctopusServiceDeleteAllTest {

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

    private OctopusApiService.AccountResponse accountResponseWithNoProperties() {
        return new OctopusApiService.AccountResponse();
    }

    @Test
    void loadAccountData_deleteAllTrue_clearsExistingTablesBeforeReload() {
        when(octopusApiService.fetchAccountData()).thenReturn(accountResponseWithNoProperties());

        octopusService.loadAccountData(true);

        verify(unitRateByHalfHourRepository).deleteAllInBatch();
        verify(standingChargeByDayRepository).deleteAllInBatch();
        verify(unitRateRepository).deleteAllInBatch();
        verify(standingChargeRepository).deleteAllInBatch();
        verify(agreementRepository).deleteAllInBatch();
        verify(meterRepository).deleteAllInBatch();
        verify(meterPointRepository).deleteAllInBatch();
    }

    @Test
    void loadAccountData_deleteAllFalse_skipsDeletion() {
        when(octopusApiService.fetchAccountData()).thenReturn(accountResponseWithNoProperties());

        octopusService.loadAccountData(false);

        verifyNoInteractions(unitRateByHalfHourRepository, standingChargeByDayRepository, unitRateRepository,
                standingChargeRepository, agreementRepository, meterRepository, meterPointRepository);
    }

    @Test
    void loadUsageData_deleteAllTrue_clearsUsageAndReloadsFromEarliestAgreement() {
        Agreement agreement = new Agreement("E-1R-TEST", LocalDateTime.parse("2021-09-26T00:00:00"), null, 1L);
        MeterPoint meterPoint = new MeterPoint("MPAN1", false, "ELEC");
        meterPoint.setId(1L);
        Meter meter = new Meter("SERIAL1", 1L);

        when(agreementRepository.findByMeterPointIdOrderByValidFrom(1L)).thenReturn(List.of(agreement));
        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of());
        when(meterPointRepository.findAll()).thenReturn(List.of(meterPoint));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(meter));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("SERIAL1"), eq(agreement.getValidFrom()), any()))
                .thenReturn(new OctopusApiService.ConsumptionResponse());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        octopusService.loadUsageData(true);

        verify(usageRepository).deleteAllInBatch();
        verify(octopusApiService).fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("SERIAL1"), eq(agreement.getValidFrom()), any());
    }

    @Test
    void loadUsageData_deleteAllFalse_resumesImmediatelyAfterLatestIntervalPerMpan_andSkipsFullDeletion() {
        MeterPoint meterPoint = new MeterPoint("MPAN1", false, "ELEC");
        meterPoint.setId(1L);
        Meter meter = new Meter("SERIAL1", 1L);

        LocalDateTime latestIntervalTo = LocalDateTime.parse("2026-01-01T13:30:00");
        UsageDateRangeProjection dateRange = mock(UsageDateRangeProjection.class);
        when(dateRange.getMpan()).thenReturn("MPAN1");
        when(dateRange.getLatest()).thenReturn(latestIntervalTo);

        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of(dateRange));
        when(meterPointRepository.findAll()).thenReturn(List.of(meterPoint));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(meter));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("SERIAL1"), eq(latestIntervalTo), any()))
                .thenReturn(new OctopusApiService.ConsumptionResponse());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        octopusService.loadUsageData(false);

        verify(usageRepository, never()).deleteAllInBatch();
        verify(octopusApiService).fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("SERIAL1"), eq(latestIntervalTo), any());
    }

    @Test
    void loadUsageData_deleteAllFalse_lagginMeterResumesFromItsOwnLatestInterval_notAnotherMetersLatest() {
        // Electricity and gas mpans are stored in the same usage table but publish on different
        // schedules; a lagging meter must resume from its own latest reading, not get skipped
        // ahead to whichever meter happens to have the most recent data.
        MeterPoint elecMeterPoint = new MeterPoint("MPAN-ELEC", false, "ELEC");
        elecMeterPoint.setId(1L);
        Meter elecMeter = new Meter("SERIAL-ELEC", 1L);
        MeterPoint gasMeterPoint = new MeterPoint("MPAN-GAS", false, "GAS");
        gasMeterPoint.setId(2L);
        Meter gasMeter = new Meter("SERIAL-GAS", 2L);

        LocalDateTime elecLatest = LocalDateTime.parse("2026-01-05T00:00:00");
        LocalDateTime gasLatest = LocalDateTime.parse("2026-01-01T00:00:00");
        UsageDateRangeProjection elecRange = mock(UsageDateRangeProjection.class);
        when(elecRange.getMpan()).thenReturn("MPAN-ELEC");
        when(elecRange.getLatest()).thenReturn(elecLatest);
        UsageDateRangeProjection gasRange = mock(UsageDateRangeProjection.class);
        when(gasRange.getMpan()).thenReturn("MPAN-GAS");
        when(gasRange.getLatest()).thenReturn(gasLatest);

        when(usageRepository.findDateRangeByMpan()).thenReturn(List.of(elecRange, gasRange));
        when(meterPointRepository.findAll()).thenReturn(List.of(elecMeterPoint, gasMeterPoint));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(elecMeter));
        when(meterRepository.findByMeterPointId(2L)).thenReturn(List.of(gasMeter));
        when(octopusApiService.fetchConsumptionData(any(), any(), any(), any(), any()))
                .thenReturn(new OctopusApiService.ConsumptionResponse());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        octopusService.loadUsageData(false);

        verify(octopusApiService).fetchConsumptionData(eq("ELEC"), eq("MPAN-ELEC"), eq("SERIAL-ELEC"), eq(elecLatest), any());
        verify(octopusApiService).fetchConsumptionData(eq("GAS"), eq("MPAN-GAS"), eq("SERIAL-GAS"), eq(gasLatest), any());
    }
}
