package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.model.StandingChargeByDay;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.StandingChargeByDayRepository;
import com.carle7.energytracker.repository.StandingChargeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OctopusServiceStandingChargeByDayTest {

    @Mock
    private AgreementRepository agreementRepository;

    @Mock
    private StandingChargeRepository standingChargeRepository;

    @Mock
    private StandingChargeByDayRepository standingChargeByDayRepository;

    @InjectMocks
    private OctopusService octopusService;

    private List<StandingChargeByDay> runAndCapture() {
        when(standingChargeByDayRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        octopusService.populateDailyStandingCharges();

        ArgumentCaptor<List<StandingChargeByDay>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(standingChargeByDayRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void gmtPeriod_usesLocalMidnightAsValidFrom() {
        // January: GMT (UTC+0), so local midnight coincides with UTC midnight.
        Agreement agreement = new Agreement("E-1R-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-04T00:00:00"), 1L);
        agreement.setId(1L);

        StandingCharge charge = new StandingCharge(1L, BigDecimal.valueOf(45), BigDecimal.valueOf(47.25), LocalDateTime.parse("2026-01-01T00:00:00"), null, "NA");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(standingChargeRepository.findByAgreementIdOrderByValidFrom(1L)).thenReturn(List.of(charge));

        List<StandingChargeByDay> days = runAndCapture();

        assertThat(days).extracting(StandingChargeByDay::getValidFrom).containsExactly(
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-02T00:00:00"),
                LocalDateTime.parse("2026-01-03T00:00:00")
        );
        assertThat(days).extracting(StandingChargeByDay::getValidTo).containsExactly(
                LocalDateTime.parse("2026-01-02T00:00:00"),
                LocalDateTime.parse("2026-01-03T00:00:00"),
                LocalDateTime.parse("2026-01-04T00:00:00")
        );
    }

    @Test
    void bstPeriod_usesTwentyThreeHundredPreviousDayAsValidFrom() {
        // 2026-06-30T23:00:00Z is local midnight London on 1 July during BST (UTC+1).
        Agreement agreement = new Agreement("E-1R-BST-TEST", LocalDateTime.parse("2026-06-30T23:00:00"), LocalDateTime.parse("2026-07-03T23:00:00"), 2L);
        agreement.setId(2L);

        StandingCharge charge = new StandingCharge(2L, BigDecimal.valueOf(45), BigDecimal.valueOf(47.25), LocalDateTime.parse("2026-06-30T23:00:00"), null, "NA");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(standingChargeRepository.findByAgreementIdOrderByValidFrom(2L)).thenReturn(List.of(charge));

        List<StandingChargeByDay> days = runAndCapture();

        assertThat(days).extracting(StandingChargeByDay::getValidFrom).containsExactly(
                LocalDateTime.parse("2026-06-30T23:00:00"),
                LocalDateTime.parse("2026-07-01T23:00:00"),
                LocalDateTime.parse("2026-07-02T23:00:00")
        );
    }

    @Test
    void priceChangeMidSeries_startsNewValueOnItsOwnLocalDay() {
        Agreement agreement = new Agreement("E-1R-CHANGE-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-05T00:00:00"), 3L);
        agreement.setId(3L);

        StandingCharge charge1 = new StandingCharge(3L, BigDecimal.valueOf(45), BigDecimal.valueOf(47.25), LocalDateTime.parse("2026-01-01T00:00:00"), null, "NA");
        StandingCharge charge2 = new StandingCharge(3L, BigDecimal.valueOf(50), BigDecimal.valueOf(52.50), LocalDateTime.parse("2026-01-03T00:00:00"), null, "NA");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(standingChargeRepository.findByAgreementIdOrderByValidFrom(3L)).thenReturn(List.of(charge1, charge2));

        List<StandingChargeByDay> days = runAndCapture();

        assertThat(days).extracting(StandingChargeByDay::getValidFrom, StandingChargeByDay::getValueExcVat)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-01T00:00:00"), BigDecimal.valueOf(45)),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-02T00:00:00"), BigDecimal.valueOf(45)),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-03T00:00:00"), BigDecimal.valueOf(50)),
                        org.assertj.core.groups.Tuple.tuple(LocalDateTime.parse("2026-01-04T00:00:00"), BigDecimal.valueOf(50))
                );
    }

    @Test
    void separatePaymentMethods_produceIndependentDailySeries() {
        Agreement agreement = new Agreement("E-1R-PM-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-03T00:00:00"), 4L);
        agreement.setId(4L);

        StandingCharge ddCharge = new StandingCharge(4L, BigDecimal.valueOf(45), BigDecimal.valueOf(47.25), LocalDateTime.parse("2026-01-01T00:00:00"), null, "DIRECT_DEBIT");
        StandingCharge nonDdCharge = new StandingCharge(4L, BigDecimal.valueOf(55), BigDecimal.valueOf(57.75), LocalDateTime.parse("2026-01-01T00:00:00"), null, "NON_DIRECT_DEBIT");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(standingChargeRepository.findByAgreementIdOrderByValidFrom(4L)).thenReturn(List.of(ddCharge, nonDdCharge));

        List<StandingChargeByDay> days = runAndCapture();

        assertThat(days).extracting(StandingChargeByDay::getPaymentMethod, StandingChargeByDay::getValidFrom)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("DIRECT_DEBIT", LocalDateTime.parse("2026-01-01T00:00:00")),
                        org.assertj.core.groups.Tuple.tuple("DIRECT_DEBIT", LocalDateTime.parse("2026-01-02T00:00:00")),
                        org.assertj.core.groups.Tuple.tuple("NON_DIRECT_DEBIT", LocalDateTime.parse("2026-01-01T00:00:00")),
                        org.assertj.core.groups.Tuple.tuple("NON_DIRECT_DEBIT", LocalDateTime.parse("2026-01-02T00:00:00"))
                );
    }

    @Test
    void openEndedAgreement_isCappedAtNinetyDaysFromNow() {
        ZoneId london = ZoneId.of("Europe/London");
        LocalDateTime start = LocalDate.now(london).atStartOfDay(london).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        Agreement agreement = new Agreement("E-1R-OPEN-TEST", start, null, 5L);
        agreement.setId(5L);

        StandingCharge charge = new StandingCharge(5L, BigDecimal.TEN, BigDecimal.TEN, start, null, "NA");

        when(agreementRepository.findAll()).thenReturn(List.of(agreement));
        when(standingChargeRepository.findByAgreementIdOrderByValidFrom(5L)).thenReturn(List.of(charge));

        List<StandingChargeByDay> days = runAndCapture();

        LocalDateTime lastDayStart = days.get(days.size() - 1).getValidFrom();
        LocalDateTime expectedWindowEnd = LocalDateTime.now().plusDays(90);

        assertThat(days.get(0).getValidFrom()).isEqualTo(start);
        assertThat(lastDayStart).isBefore(expectedWindowEnd);
        assertThat(lastDayStart).isAfter(expectedWindowEnd.minusDays(2));
    }
}
