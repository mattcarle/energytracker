package com.carle7.energytracker.service;

import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.UtcToLocal;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import com.carle7.energytracker.repository.UtcToLocalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OctopusServiceUtcToLocalTest {

    @Mock
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @Mock
    private UtcToLocalRepository utcToLocalRepository;

    @InjectMocks
    private OctopusService octopusService;

    private List<UtcToLocal> runAndCapture(LocalDateTime earliestValidFrom, LocalDateTime latestValidTo) {
        UnitRateByHalfHour first = new UnitRateByHalfHour(1L, BigDecimal.TEN, BigDecimal.TEN, earliestValidFrom, null, "NA", "STANDARD");
        UnitRateByHalfHour last = new UnitRateByHalfHour(1L, BigDecimal.TEN, BigDecimal.TEN, latestValidTo.minusMinutes(30), latestValidTo, "NA", "STANDARD");

        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.of(first));
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.of(last));
        when(utcToLocalRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        octopusService.populateUtcToLocalMapping();

        ArgumentCaptor<List<UtcToLocal>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(utcToLocalRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void earliestRecord_isFlooredToLocalMidnight_inGmt() {
        // Earliest half-hourly data starts mid-afternoon; the mapping should still start at
        // local midnight for that day (00:00 UTC in GMT).
        List<UtcToLocal> mappings = runAndCapture(LocalDateTime.parse("2026-01-15T14:00:00"), LocalDateTime.parse("2026-01-15T15:00:00"));

        assertThat(mappings.get(0).getUtcTime()).isEqualTo(LocalDateTime.parse("2026-01-15T00:00:00"));
        assertThat(mappings.get(0).getLocalTime().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(mappings.get(0).getTimeZone()).isEqualTo("GMT");
    }

    @Test
    void earliestRecord_isFlooredToLocalMidnight_inBst() {
        // Earliest half-hourly data starts mid-afternoon during BST; local midnight for that day
        // is 23:00 UTC the previous day.
        List<UtcToLocal> mappings = runAndCapture(LocalDateTime.parse("2026-07-15T13:00:00"), LocalDateTime.parse("2026-07-15T14:00:00"));

        assertThat(mappings.get(0).getUtcTime()).isEqualTo(LocalDateTime.parse("2026-07-14T23:00:00"));
        assertThat(mappings.get(0).getLocalTime()).isEqualTo(LocalDateTime.parse("2026-07-15T00:00:00"));
        assertThat(mappings.get(0).getTimeZone()).isEqualTo("BST");
    }

    @Test
    void spansGmtToBstTransition_withCorrectLocalTimeAndAbbreviation() {
        // Clocks go forward at 01:00 UTC on 2026-03-29 (BST starts). Earliest slot is already at
        // local midnight (00:00 UTC, GMT), so no further flooring occurs.
        List<UtcToLocal> mappings = runAndCapture(LocalDateTime.parse("2026-03-29T00:00:00"), LocalDateTime.parse("2026-03-29T02:00:00"));

        assertThat(mappings).extracting(UtcToLocal::getUtcTime, UtcToLocal::getLocalTime, UtcToLocal::getTimeZone)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-03-29T00:00:00"), LocalDateTime.parse("2026-03-29T00:00:00"), "GMT"),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-03-29T00:30:00"), LocalDateTime.parse("2026-03-29T00:30:00"), "GMT"),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-03-29T01:00:00"), LocalDateTime.parse("2026-03-29T02:00:00"), "BST"),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-03-29T01:30:00"), LocalDateTime.parse("2026-03-29T02:30:00"), "BST")
                );
    }

    @Test
    void noHalfHourlyData_producesNoMappingsAndSkipsSave() {
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.empty());
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.empty());

        int count = octopusService.populateUtcToLocalMapping();

        assertThat(count).isZero();
        org.mockito.Mockito.verifyNoInteractions(utcToLocalRepository);
    }
}
