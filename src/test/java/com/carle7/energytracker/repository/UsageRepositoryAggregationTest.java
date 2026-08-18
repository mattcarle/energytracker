package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.model.UtcToLocal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UsageRepositoryAggregationTest {

    @Autowired
    private UsageRepository usageRepository;

    @Autowired
    private MeterPointRepository meterPointRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @Autowired
    private UtcToLocalRepository utcToLocalRepository;

    @Test
    void byHalfHour_returnsOneRowPerInterval_filteringByPaymentMethodAndDateRange() {
        String mpan = "8234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(new Agreement("E-1R-TEST-HH", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        // Two DIRECT_DEBIT half-hour slots on Jan 5, in range.
        List<LocalDateTime> inRangeSlots = List.of(
                LocalDateTime.parse("2026-01-05T10:00:00"),
                LocalDateTime.parse("2026-01-05T10:30:00")
        );
        for (LocalDateTime slot : inRangeSlots) {
            seedSlot(agreement.getId(), mpan, slot, BigDecimal.valueOf(20), "DIRECT_DEBIT");
        }

        // A NON_DIRECT_DEBIT slot that must be excluded by the default payment method filter.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T11:00:00"), BigDecimal.valueOf(20), "NON_DIRECT_DEBIT");

        // A DIRECT_DEBIT slot on Jan 6 that must be excluded by the date range filter.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-06T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");

        List<UsageByHalfHourProjection> results = usageRepository.findUsageByHalfHour(
                mpan, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-06"), List.of("DIRECT_DEBIT", "NA"));

        assertThat(results).hasSize(2);

        UsageByHalfHourProjection first = results.get(0);
        assertThat(first.getMpan()).isEqualTo(mpan);
        assertThat(first.getMeterType()).isEqualTo("ELEC");
        assertThat(first.getIsExport()).isFalse();
        assertThat(first.getUsageInterval()).isEqualTo(LocalDateTime.parse("2026-01-05T10:00:00"));
        assertThat(first.getIntervalCount()).isEqualTo(1L);
        assertThat(first.getKwh()).isEqualByComparingTo("1.0000");
        assertThat(first.getCost()).isEqualByComparingTo("0.2000");
        assertThat(first.getAvgRate()).isEqualByComparingTo("0.2000");

        UsageByHalfHourProjection second = results.get(1);
        assertThat(second.getUsageInterval()).isEqualTo(LocalDateTime.parse("2026-01-05T10:30:00"));
    }

    @Test
    void byDay_aggregatesConsumptionAndCostByLocalDay_filteringByPaymentMethodAndDateRange() {
        String mpan = "1234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(new Agreement("E-1R-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        // Two DIRECT_DEBIT half-hour slots on each of Jan 5 and Jan 6, in range.
        List<LocalDateTime> inRangeSlots = List.of(
                LocalDateTime.parse("2026-01-05T10:00:00"),
                LocalDateTime.parse("2026-01-05T10:30:00"),
                LocalDateTime.parse("2026-01-06T10:00:00"),
                LocalDateTime.parse("2026-01-06T10:30:00")
        );
        for (LocalDateTime slot : inRangeSlots) {
            seedSlot(agreement.getId(), mpan, slot, BigDecimal.valueOf(20), "DIRECT_DEBIT");
        }

        // A NON_DIRECT_DEBIT slot on Jan 5 that must be excluded by the default payment method filter.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T11:00:00"), BigDecimal.valueOf(20), "NON_DIRECT_DEBIT");

        // A DIRECT_DEBIT slot on Jan 4 that must be excluded by the date range filter.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-04T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");

        List<UsageByDayProjection> results = usageRepository.findUsageByDay(
                mpan, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-07"), List.of("DIRECT_DEBIT", "NA"));

        assertThat(results).hasSize(2);

        UsageByDayProjection day1 = results.get(0);
        assertThat(day1.getMpan()).isEqualTo(mpan);
        assertThat(day1.getMeterType()).isEqualTo("ELEC");
        assertThat(day1.getIsExport()).isFalse();
        assertThat(day1.getUsageDate()).isEqualTo(LocalDate.parse("2026-01-05"));
        assertThat(day1.getIntervalCount()).isEqualTo(2L);
        assertThat(day1.getKwh()).isEqualByComparingTo("2.0000");
        assertThat(day1.getCost()).isEqualByComparingTo("0.4000");
        assertThat(day1.getAvgRate()).isEqualByComparingTo("0.2000");

        UsageByDayProjection day2 = results.get(1);
        assertThat(day2.getUsageDate()).isEqualTo(LocalDate.parse("2026-01-06"));
        assertThat(day2.getIntervalCount()).isEqualTo(2L);
    }

    @Test
    void byWeek_aggregatesAcrossDaysWithinEachIsoWeekStartingMonday() {
        String mpan = "4234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(new Agreement("E-1R-TEST-W", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        // 2026-01-05 is a Monday. Two slots in that ISO week (different days), one slot in the
        // following week (starting 2026-01-12).
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-08T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-12T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");

        List<UsageByWeekProjection> results = usageRepository.findUsageByWeek(
                mpan, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-19"), List.of("DIRECT_DEBIT", "NA"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUsageWeek()).isEqualTo(LocalDate.parse("2026-01-05"));
        assertThat(results.get(0).getIntervalCount()).isEqualTo(2L);
        assertThat(results.get(0).getKwh()).isEqualByComparingTo("2.0000");
        assertThat(results.get(1).getUsageWeek()).isEqualTo(LocalDate.parse("2026-01-12"));
        assertThat(results.get(1).getIntervalCount()).isEqualTo(1L);
    }

    @Test
    void byMonth_aggregatesAcrossDaysWithinEachCalendarMonth() {
        String mpan = "2234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(new Agreement("E-1R-TEST-M", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        // Two slots in January (different days), one slot in February.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-20T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-02-10T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");

        List<UsageByMonthProjection> results = usageRepository.findUsageByMonth(
                mpan, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-01"), List.of("DIRECT_DEBIT", "NA"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUsageMonth()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(results.get(0).getIntervalCount()).isEqualTo(2L);
        assertThat(results.get(0).getKwh()).isEqualByComparingTo("2.0000");
        assertThat(results.get(1).getUsageMonth()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(results.get(1).getIntervalCount()).isEqualTo(1L);
    }

    @Test
    void byYear_aggregatesAcrossMonthsWithinEachCalendarYear() {
        String mpan = "3234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(new Agreement("E-1R-TEST-Y", LocalDateTime.parse("2025-01-01T00:00:00"), null, meterPoint.getId()));

        // Two slots in 2025 (different months), one slot in 2026.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2025-03-05T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2025-11-20T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-02-10T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");

        List<UsageByYearProjection> results = usageRepository.findUsageByYear(
                mpan, LocalDate.parse("2025-01-01"), LocalDate.parse("2027-01-01"), List.of("DIRECT_DEBIT", "NA"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUsageYear()).isEqualTo(LocalDate.parse("2025-01-01"));
        assertThat(results.get(0).getIntervalCount()).isEqualTo(2L);
        assertThat(results.get(0).getKwh()).isEqualByComparingTo("2.0000");
        assertThat(results.get(1).getUsageYear()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(results.get(1).getIntervalCount()).isEqualTo(1L);
    }

    @Test
    void byDay_countsMissingPlaceholderIntervalsSeparatelyFromIntervalCount() {
        String mpan = "5234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "GAS"));
        Agreement agreement = agreementRepository.save(new Agreement("G-1R-TEST-MISSING", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        // One real reading, one data-integrity-check placeholder (see DataIntegrityService), both
        // on the same day.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T10:00:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT");
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T10:30:00"), BigDecimal.valueOf(20), "DIRECT_DEBIT", true);

        List<UsageByDayProjection> results = usageRepository.findUsageByDay(
                mpan, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-06"), List.of("DIRECT_DEBIT", "NA"));

        assertThat(results).hasSize(1);
        UsageByDayProjection day = results.get(0);
        assertThat(day.getIntervalCount()).isEqualTo(2L);
        assertThat(day.getMissingIntervalCount()).isEqualTo(1L);
        assertThat(day.getKwh()).isEqualByComparingTo("1.0000");
    }

    private void seedSlot(Long agreementId, String mpan, LocalDateTime slot, BigDecimal valueIncVat, String paymentMethod) {
        seedSlot(agreementId, mpan, slot, valueIncVat, paymentMethod, false);
    }

    private void seedSlot(Long agreementId, String mpan, LocalDateTime slot, BigDecimal valueIncVat, String paymentMethod, boolean missing) {
        unitRateByHalfHourRepository.save(new UnitRateByHalfHour(agreementId, valueIncVat, valueIncVat, slot, slot.plusMinutes(30), paymentMethod, "STANDARD"));
        usageRepository.save(new Usage(slot, slot.plusMinutes(30), missing ? BigDecimal.ZERO : BigDecimal.ONE, mpan, missing));
        utcToLocalRepository.save(new UtcToLocal(slot, slot, "GMT"));
    }
}
