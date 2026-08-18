package com.carle7.energytracker.service;

import com.carle7.energytracker.dto.DataIntegrityReport;
import com.carle7.energytracker.dto.IntegrityCheckResult;
import com.carle7.energytracker.dto.MpanIntegrityReport;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.IntervalProjection;
import com.carle7.energytracker.repository.MeterPointRepository;
import com.carle7.energytracker.repository.StandingChargeByDayRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import com.carle7.energytracker.repository.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataIntegrityServiceTest {

    @Mock
    private MeterPointRepository meterPointRepository;

    @Mock
    private AgreementRepository agreementRepository;

    @Mock
    private StandingChargeByDayRepository standingChargeByDayRepository;

    @Mock
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @Mock
    private UsageRepository usageRepository;

    @InjectMocks
    private DataIntegrityService dataIntegrityService;

    private record TestInterval(LocalDateTime validFrom, LocalDateTime validTo) implements IntervalProjection {
        @Override
        public LocalDateTime getValidFrom() {
            return validFrom;
        }

        @Override
        public LocalDateTime getValidTo() {
            return validTo;
        }
    }

    private MeterPoint gasMeterPoint(String mpan) {
        MeterPoint meterPoint = new MeterPoint(mpan, false, "GAS");
        meterPoint.setId(1L);
        return meterPoint;
    }

    // UK clocks go back at 2am BST -> 1am GMT on the last Sunday of October. The half-hour
    // reading spanning that instant is genuinely stored with validTo (01:00) reading earlier
    // than validFrom (01:30) - correct per how the transition instant resolves, not corrupt
    // data - and the repeated 01:00-01:59 hour produces two real readings sharing identical
    // (validFrom, validTo) labels. Both should be recognised as present data, not gaps.
    @Test
    void usageCheckIgnoresDstFallBackArtifactsAsGaps() {
        String mpan = "1234567890123";
        when(meterPointRepository.findAll()).thenReturn(List.of(gasMeterPoint(mpan)));
        when(agreementRepository.findByMeterPointIdOrderByValidFrom(anyLong())).thenReturn(List.of());
        when(standingChargeByDayRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of());
        when(unitRateByHalfHourRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of());

        LocalDateTime d = LocalDateTime.of(2025, 10, 26, 0, 0);
        when(usageRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of(
                new TestInterval(d.withHour(0).withMinute(30), d.withHour(1).withMinute(0)),
                new TestInterval(d.withHour(1).withMinute(0), d.withHour(1).withMinute(30)),  // BST occurrence
                new TestInterval(d.withHour(1).withMinute(0), d.withHour(1).withMinute(30)),  // GMT occurrence (duplicate)
                new TestInterval(d.withHour(1).withMinute(30), d.withHour(1).withMinute(0)),  // straddles the clock change
                new TestInterval(d.withHour(1).withMinute(30), d.withHour(2).withMinute(0)),  // GMT occurrence
                new TestInterval(d.withHour(2).withMinute(0), d.withHour(2).withMinute(30))
        ));

        DataIntegrityReport report = dataIntegrityService.checkDataIntegrity();

        IntegrityCheckResult usage = report.getMpans().get(0).getUsage();
        assertThat(usage.getGaps()).isEmpty();
        assertThat(usage.getEarliest()).isEqualTo(d.withHour(0).withMinute(30));
        assertThat(usage.getLatest()).isEqualTo(d.withHour(2).withMinute(30));
        // No real gap found, so nothing should have been backfilled.
        verify(usageRepository, never()).saveAll(any());
    }

    @Test
    void usageCheckStillReportsARealGap() {
        String mpan = "1234567890123";
        when(meterPointRepository.findAll()).thenReturn(List.of(gasMeterPoint(mpan)));
        when(agreementRepository.findByMeterPointIdOrderByValidFrom(anyLong())).thenReturn(List.of());
        when(standingChargeByDayRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of());
        when(unitRateByHalfHourRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of());

        LocalDateTime beforeGap = LocalDateTime.of(2025, 1, 6, 0, 0);
        LocalDateTime afterGap = LocalDateTime.of(2025, 2, 7, 14, 0);
        when(usageRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of(
                new TestInterval(beforeGap.minusMinutes(30), beforeGap),
                new TestInterval(afterGap, afterGap.plusMinutes(30))
        ));

        DataIntegrityReport report = dataIntegrityService.checkDataIntegrity();

        IntegrityCheckResult usage = report.getMpans().get(0).getUsage();
        assertThat(usage.getGaps()).hasSize(1);
        assertThat(usage.getGaps().get(0).getFrom()).isEqualTo(beforeGap);
        assertThat(usage.getGaps().get(0).getTo()).isEqualTo(afterGap);
    }

    // Every check run should start clean, so a gap that's since been backfilled with real data
    // doesn't keep getting re-filled with a now-stale placeholder.
    @Test
    void checkClearsPreviousPlaceholdersBeforeScanning() {
        when(meterPointRepository.findAll()).thenReturn(List.of());

        dataIntegrityService.checkDataIntegrity();

        verify(usageRepository).deleteByMissingTrue();
    }

    @Test
    void usageGapIsBackfilledWithZeroConsumptionPlaceholders() {
        String mpan = "1234567890123";
        when(meterPointRepository.findAll()).thenReturn(List.of(gasMeterPoint(mpan)));
        when(agreementRepository.findByMeterPointIdOrderByValidFrom(anyLong())).thenReturn(List.of());
        when(standingChargeByDayRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of());
        when(unitRateByHalfHourRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of());

        LocalDateTime beforeGap = LocalDateTime.of(2025, 6, 1, 1, 0);
        LocalDateTime afterGap = beforeGap.plusHours(1);
        when(usageRepository.findDistinctIntervalsByMpan(mpan)).thenReturn(List.of(
                new TestInterval(beforeGap.minusMinutes(30), beforeGap),
                new TestInterval(afterGap, afterGap.plusMinutes(30))
        ));

        dataIntegrityService.checkDataIntegrity();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Usage>> captor = ArgumentCaptor.forClass(List.class);
        verify(usageRepository).saveAll(captor.capture());
        List<Usage> placeholders = captor.getValue();

        assertThat(placeholders).hasSize(2);
        assertThat(placeholders).allSatisfy(usage -> {
            assertThat(usage.getMpan()).isEqualTo(mpan);
            assertThat(usage.getConsumption()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(usage.isMissing()).isTrue();
        });
        assertThat(placeholders.get(0).getIntervalFrom()).isEqualTo(beforeGap);
        assertThat(placeholders.get(0).getIntervalTo()).isEqualTo(beforeGap.plusMinutes(30));
        assertThat(placeholders.get(1).getIntervalFrom()).isEqualTo(beforeGap.plusMinutes(30));
        assertThat(placeholders.get(1).getIntervalTo()).isEqualTo(afterGap);
    }
}
