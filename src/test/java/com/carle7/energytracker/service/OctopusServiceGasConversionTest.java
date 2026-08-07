package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.MeterPointRepository;
import com.carle7.energytracker.repository.MeterRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import com.carle7.energytracker.repository.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OctopusServiceGasConversionTest {

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
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @InjectMocks
    private OctopusService octopusService;

    private OctopusApiService.ConsumptionResponse responseOf(double consumption) {
        OctopusApiService.ConsumptionDto dto = new OctopusApiService.ConsumptionDto();
        dto.consumption = consumption;
        dto.interval_start = "2026-01-01T00:30:00Z";
        dto.interval_end = "2026-01-01T01:00:00Z";

        OctopusApiService.ConsumptionResponse response = new OctopusApiService.ConsumptionResponse();
        response.results = List.of(dto);
        return response;
    }

    @Test
    void gasConsumption_isConvertedFromCubicMetresToKwh() {
        MeterPoint gasMeterPoint = new MeterPoint("1234567890", false, "GAS");
        gasMeterPoint.setId(1L);
        Meter gasMeter = new Meter("GAS-SERIAL", 1L);

        Usage priorUsage = new Usage(LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T00:30:00"),
                java.math.BigDecimal.ONE, "1234567890");

        when(usageRepository.findFirstByOrderByIntervalToDesc()).thenReturn(Optional.of(priorUsage));
        when(meterPointRepository.findAll()).thenReturn(List.of(gasMeterPoint));
        when(meterRepository.findByMeterPointId(1L)).thenReturn(List.of(gasMeter));
        when(octopusApiService.fetchConsumptionData(eq("GAS"), eq("1234567890"), eq("GAS-SERIAL"), any(), any()))
                .thenReturn(responseOf(1.0));
        when(usageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        octopusService.loadUsageData(false);

        ArgumentCaptor<List<Usage>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(usageRepository).saveAll(captor.capture());

        // 1 m³ × 1.02264 (volume correction) × 40.0 MJ/m³ (calorific value) ÷ 3.6 = 11.3627 kWh
        assertThat(captor.getValue().get(0).getConsumption()).isEqualByComparingTo("11.3627");
    }

    @Test
    void electricityConsumption_isSavedUnconverted() {
        MeterPoint elecMeterPoint = new MeterPoint("2000016292581", false, "ELEC");
        elecMeterPoint.setId(2L);
        Meter elecMeter = new Meter("ELEC-SERIAL", 2L);

        Usage priorUsage = new Usage(LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T00:30:00"),
                java.math.BigDecimal.ONE, "2000016292581");

        when(usageRepository.findFirstByOrderByIntervalToDesc()).thenReturn(Optional.of(priorUsage));
        when(meterPointRepository.findAll()).thenReturn(List.of(elecMeterPoint));
        when(meterRepository.findByMeterPointId(2L)).thenReturn(List.of(elecMeter));
        when(octopusApiService.fetchConsumptionData(eq("ELEC"), eq("2000016292581"), eq("ELEC-SERIAL"), any(), any()))
                .thenReturn(responseOf(1.234));
        when(usageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        octopusService.loadUsageData(false);

        ArgumentCaptor<List<Usage>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(usageRepository).saveAll(captor.capture());

        assertThat(captor.getValue().get(0).getConsumption()).isEqualByComparingTo("1.234");
    }
}
