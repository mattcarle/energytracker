package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.model.UnitRate;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.DayAndNightTariffRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import com.carle7.energytracker.repository.UnitRateRepository;
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
class OctopusServiceTest {

    @Mock
    private AgreementRepository agreementRepository;

    @Mock
    private UnitRateRepository unitRateRepository;

    @Mock
    private DayAndNightTariffRepository dayAndNightTariffRepository;

    @Mock
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @InjectMocks
    private OctopusService octopusService;

    private List<UnitRateByHalfHour> runAndCapture() {
        when(unitRateByHalfHourRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        octopusService.populateHalfHourlyUnitRates();

        ArgumentCaptor<List<UnitRateByHalfHour>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(unitRateByHalfHourRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void standardTariff_fillsGapsBetweenUnitRateRecords() {
        Agreement agreement = new Agreement("E-1R-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-02T02:30:00"), 1L);
        agreement.setId(1L);

        UnitRate rate1 = new UnitRate(1L, BigDecimal.valueOf(10), BigDecimal.valueOf(10.5), LocalDateTime.parse("2026-01-01T23:30:00"), null, "NA", "STANDARD");
        UnitRate rate2 = new UnitRate(1L, BigDecimal.valueOf(20), BigDecimal.valueOf(20.5), LocalDateTime.parse("2026-01-02T01:30:00"), null, "NA", "STANDARD");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(unitRateRepository.findByAgreementIdOrderByValidFrom(1L)).thenReturn(List.of(rate1, rate2));
        when(dayAndNightTariffRepository.findByTariffCode("E-1R-TEST")).thenReturn(Optional.empty());

        List<UnitRateByHalfHour> slots = runAndCapture();

        assertThat(slots).extracting(UnitRateByHalfHour::getValidFrom).containsExactly(
                LocalDateTime.parse("2026-01-01T23:30:00"),
                LocalDateTime.parse("2026-01-02T00:00:00"),
                LocalDateTime.parse("2026-01-02T00:30:00"),
                LocalDateTime.parse("2026-01-02T01:00:00"),
                LocalDateTime.parse("2026-01-02T01:30:00"),
                LocalDateTime.parse("2026-01-02T02:00:00")
        );
        assertThat(slots.subList(0, 4)).extracting(UnitRateByHalfHour::getValueExcVat)
                .containsOnly(BigDecimal.valueOf(10));
        assertThat(slots.subList(4, 6)).extracting(UnitRateByHalfHour::getValueExcVat)
                .containsOnly(BigDecimal.valueOf(20));
    }

    @Test
    void dayAndNightTariff_picksCorrectRateInGmt() {
        Agreement agreement = new Agreement("E-1R-DN-TEST", LocalDateTime.parse("2026-01-15T06:00:00"), LocalDateTime.parse("2026-01-15T07:30:00"), 2L);
        agreement.setId(2L);

        DayAndNightTariff dnt = new DayAndNightTariff("E-1R-DN-TEST", java.time.LocalTime.of(23, 0), java.time.LocalTime.of(7, 0));

        UnitRate dayRate = new UnitRate(2L, BigDecimal.valueOf(100), BigDecimal.valueOf(100.5), LocalDateTime.parse("2026-01-15T06:00:00"), null, "NA", "DAY");
        UnitRate nightRate = new UnitRate(2L, BigDecimal.valueOf(50), BigDecimal.valueOf(50.5), LocalDateTime.parse("2026-01-15T06:00:00"), null, "NA", "NIGHT");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(unitRateRepository.findByAgreementIdOrderByValidFrom(2L)).thenReturn(List.of(dayRate, nightRate));
        when(dayAndNightTariffRepository.findByTariffCode("E-1R-DN-TEST")).thenReturn(Optional.of(dnt));

        List<UnitRateByHalfHour> slots = runAndCapture();

        assertThat(slots).extracting(UnitRateByHalfHour::getValidFrom, UnitRateByHalfHour::getRateType, UnitRateByHalfHour::getValueExcVat)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-15T06:00:00"), "NIGHT", BigDecimal.valueOf(50)),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-15T06:30:00"), "NIGHT", BigDecimal.valueOf(50)),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-15T07:00:00"), "DAY", BigDecimal.valueOf(100))
                );
    }

    @Test
    void dayAndNightTariff_appliesBritishSummerTimeOffset() {
        // Same UTC clock times as the GMT test, but in July (BST, UTC+1). Local time is shifted
        // an hour later, so the day/night classification flips relative to the GMT case above.
        Agreement agreement = new Agreement("E-1R-DN-BST-TEST", LocalDateTime.parse("2026-07-15T05:30:00"), LocalDateTime.parse("2026-07-15T07:00:00"), 4L);
        agreement.setId(4L);

        DayAndNightTariff dnt = new DayAndNightTariff("E-1R-DN-BST-TEST", java.time.LocalTime.of(23, 0), java.time.LocalTime.of(7, 0));

        UnitRate dayRate = new UnitRate(4L, BigDecimal.valueOf(100), BigDecimal.valueOf(100.5), LocalDateTime.parse("2026-07-15T05:30:00"), null, "NA", "DAY");
        UnitRate nightRate = new UnitRate(4L, BigDecimal.valueOf(50), BigDecimal.valueOf(50.5), LocalDateTime.parse("2026-07-15T05:30:00"), null, "NA", "NIGHT");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(unitRateRepository.findByAgreementIdOrderByValidFrom(4L)).thenReturn(List.of(dayRate, nightRate));
        when(dayAndNightTariffRepository.findByTariffCode("E-1R-DN-BST-TEST")).thenReturn(Optional.of(dnt));

        List<UnitRateByHalfHour> slots = runAndCapture();

        assertThat(slots).extracting(UnitRateByHalfHour::getValidFrom, UnitRateByHalfHour::getRateType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-07-15T05:30:00"), "NIGHT"),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-07-15T06:00:00"), "DAY"),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-07-15T06:30:00"), "DAY")
                );
    }

    @Test
    void openEndedAgreement_isCappedAtNinetyDaysFromNow() {
        LocalDateTime start = LocalDateTime.now().withSecond(0).withNano(0);
        start = start.getMinute() < 30 ? start.withMinute(0) : start.withMinute(30);

        Agreement agreement = new Agreement("E-1R-OPEN-TEST", start, null, 3L);
        agreement.setId(3L);

        UnitRate rate = new UnitRate(3L, BigDecimal.TEN, BigDecimal.TEN, start, null, "NA", "STANDARD");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(unitRateRepository.findByAgreementIdOrderByValidFrom(3L)).thenReturn(List.of(rate));
        when(dayAndNightTariffRepository.findByTariffCode("E-1R-OPEN-TEST")).thenReturn(Optional.empty());

        List<UnitRateByHalfHour> slots = runAndCapture();

        LocalDateTime lastSlotStart = slots.get(slots.size() - 1).getValidFrom();
        LocalDateTime expectedWindowEnd = LocalDateTime.now().plusDays(90);

        assertThat(slots.get(0).getValidFrom()).isEqualTo(start);
        assertThat(lastSlotStart).isBefore(expectedWindowEnd.plusMinutes(1));
        assertThat(lastSlotStart).isAfter(expectedWindowEnd.minusHours(1));
    }

    @Test
    void lastRecordExtendingPastAgreementValidTo_isCappedAtAgreementWindow() {
        // Reproduces real Octopus data: a superseded unit_rate record's own valid_to can extend
        // well past the agreement's own valid_to. Generation must stop at the agreement's window,
        // not the record's, otherwise it overlaps the next agreement's legitimate slots.
        Agreement agreement = new Agreement("E-1R-OVERRUN-TEST", LocalDateTime.parse("2022-01-01T00:00:00"), LocalDateTime.parse("2022-01-02T00:00:00"), 5L);
        agreement.setId(5L);

        UnitRate rate = new UnitRate(5L, BigDecimal.valueOf(15), BigDecimal.valueOf(15.5), LocalDateTime.parse("2022-01-01T00:00:00"), LocalDateTime.parse("2022-02-01T00:00:00"), "NA", "STANDARD");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(unitRateRepository.findByAgreementIdOrderByValidFrom(5L)).thenReturn(List.of(rate));
        when(dayAndNightTariffRepository.findByTariffCode("E-1R-OVERRUN-TEST")).thenReturn(Optional.empty());

        List<UnitRateByHalfHour> slots = runAndCapture();

        assertThat(slots).extracting(UnitRateByHalfHour::getValidFrom)
                .allSatisfy(validFrom -> assertThat(validFrom).isBefore(agreement.getValidTo()));
        assertThat(slots.get(slots.size() - 1).getValidFrom()).isEqualTo(LocalDateTime.parse("2022-01-01T23:30:00"));
    }
}
