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

        when(agreementRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.of(agreement));
        when(usageRepository.findFirstByOrderByIntervalToDesc()).thenReturn(Optional.empty());
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
    void loadUsageData_deleteAllFalse_reloadsFromStartOfLatestDay_andSkipsFullDeletion() {
        // Latest saved interval is mid-afternoon, not midnight, so this also confirms the reload
        // point is the start of that interval's calendar day rather than the interval itself.
        com.carle7.energytracker.model.Usage priorUsage = new com.carle7.energytracker.model.Usage(
                LocalDateTime.parse("2026-01-01T13:00:00"), LocalDateTime.parse("2026-01-01T13:30:00"),
                java.math.BigDecimal.ONE, "MPAN1");
        MeterPoint meterPoint = new MeterPoint("MPAN1", false, "ELEC");
        meterPoint.setId(1L);
        Meter meter = new Meter("SERIAL1", 1L);

        LocalDateTime startOfLatestDay = LocalDateTime.parse("2026-01-01T00:00:00");

        when(usageRepository.findFirstByOrderByIntervalToDesc()).thenReturn(Optional.of(priorUsage));
        when(meterPointRepository.findAll()).thenReturn(List.of(meterPoint));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(meter));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("SERIAL1"), eq(startOfLatestDay), any()))
                .thenReturn(new OctopusApiService.ConsumptionResponse());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        octopusService.loadUsageData(false);

        verify(usageRepository, never()).deleteAllInBatch();
        verify(usageRepository).deleteByMpanAndIntervalFromGreaterThanEqual("MPAN1", startOfLatestDay);
        verify(octopusApiService).fetchConsumptionData(eq("ELEC"), eq("MPAN1"), eq("SERIAL1"), eq(startOfLatestDay), any());
    }
}
