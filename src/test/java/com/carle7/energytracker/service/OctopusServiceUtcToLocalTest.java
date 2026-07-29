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

    private List<UtcToLocal> runAndCapture() {
        when(utcToLocalRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        octopusService.populateUtcToLocalMapping();

        ArgumentCaptor<List<UtcToLocal>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(utcToLocalRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void spansGmtToBstTransition_withCorrectLocalTimeAndAbbreviation() {
        // Clocks go forward at 01:00 UTC on 2026-03-29 (BST starts).
        UnitRateByHalfHour first = new UnitRateByHalfHour(1L, BigDecimal.TEN, BigDecimal.TEN, LocalDateTime.parse("2026-03-29T00:30:00"), null, "NA", "STANDARD");
        UnitRateByHalfHour last = new UnitRateByHalfHour(1L, BigDecimal.TEN, BigDecimal.TEN, LocalDateTime.parse("2026-03-29T01:30:00"), LocalDateTime.parse("2026-03-29T02:00:00"), "NA", "STANDARD");

        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc()).thenReturn(Optional.of(first));
        when(unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc()).thenReturn(Optional.of(last));

        List<UtcToLocal> mappings = runAndCapture();

        assertThat(mappings).extracting(UtcToLocal::getUtcTime, UtcToLocal::getLocalTime, UtcToLocal::getTimeZone)
                .containsExactly(
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
