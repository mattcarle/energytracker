package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.UtcToLocal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UsageRepositoryGroupByRateAndRateTypeTest {

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
    void byHalfHour_returnsOneRateTypeRowPerInterval() {
        String mpan = "9234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(
                new Agreement("E-2R-DAY-NIGHT-TEST-HH", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T09:00:00"), BigDecimal.valueOf(2), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T23:00:00"), BigDecimal.valueOf(1), "NIGHT", BigDecimal.valueOf(10));

        // Outside the requested interval bounds - must be excluded.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-06T00:00:00"), BigDecimal.valueOf(99), "DAY", BigDecimal.valueOf(20));

        List<UsageByHalfHourGroupByRateAndRateTypeProjection> results = usageRepository.findUsageByHalfHourGroupByRateAndRateType(
                mpan, LocalDateTime.parse("2026-01-05T00:00:00"), LocalDateTime.parse("2026-01-06T00:00:00"));

        assertThat(results).hasSize(2);

        UsageByHalfHourGroupByRateAndRateTypeProjection dayRow = results.stream()
                .filter(r -> r.getUsageInterval().equals(LocalDateTime.parse("2026-01-05T09:00:00"))).findFirst().orElseThrow();
        assertThat(dayRow.getMpan()).isEqualTo(mpan);
        assertThat(dayRow.getRateType()).isEqualTo("DAY");
        assertThat(dayRow.getRate()).isEqualByComparingTo("20");
        assertThat(dayRow.getKwh()).isEqualByComparingTo("2.0000");

        UsageByHalfHourGroupByRateAndRateTypeProjection nightRow = results.stream()
                .filter(r -> r.getUsageInterval().equals(LocalDateTime.parse("2026-01-05T23:00:00"))).findFirst().orElseThrow();
        assertThat(nightRow.getRateType()).isEqualTo("NIGHT");
        assertThat(nightRow.getRate()).isEqualByComparingTo("10");
        assertThat(nightRow.getKwh()).isEqualByComparingTo("1.0000");
    }

    @Test
    void byDay_sumsConsumptionSeparatelyPerRateTypeAndFiltersByIntervalBounds() {
        String mpan = "5234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(
                new Agreement("E-2R-DAY-NIGHT-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T09:00:00"), BigDecimal.valueOf(2), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T09:30:00"), BigDecimal.valueOf(3), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T23:00:00"), BigDecimal.valueOf(1), "NIGHT", BigDecimal.valueOf(10));

        // Outside the requested interval bounds - must be excluded.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-04T09:00:00"), BigDecimal.valueOf(99), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-06T00:00:00"), BigDecimal.valueOf(99), "DAY", BigDecimal.valueOf(20));

        List<UsageByDayGroupByRateAndRateTypeProjection> results = usageRepository.findUsageByDayGroupByRateAndRateType(
                mpan, LocalDateTime.parse("2026-01-05T00:00:00"), LocalDateTime.parse("2026-01-06T00:00:00"));

        assertThat(results).hasSize(2);

        UsageByDayGroupByRateAndRateTypeProjection dayRow = results.stream()
                .filter(r -> r.getRateType().equals("DAY")).findFirst().orElseThrow();
        assertThat(dayRow.getMpan()).isEqualTo(mpan);
        assertThat(dayRow.getUsageDate()).isEqualTo(java.time.LocalDate.parse("2026-01-05"));
        assertThat(dayRow.getRate()).isEqualByComparingTo("20");
        assertThat(dayRow.getKwh()).isEqualByComparingTo("5.0000");

        UsageByDayGroupByRateAndRateTypeProjection nightRow = results.stream()
                .filter(r -> r.getRateType().equals("NIGHT")).findFirst().orElseThrow();
        assertThat(nightRow.getRate()).isEqualByComparingTo("10");
        assertThat(nightRow.getKwh()).isEqualByComparingTo("1.0000");
    }

    @Test
    void byMonth_aggregatesAcrossDaysSeparatelyPerRateType() {
        String mpan = "6234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(
                new Agreement("E-2R-DAY-NIGHT-TEST-M", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));

        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-05T09:00:00"), BigDecimal.valueOf(2), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-20T09:00:00"), BigDecimal.valueOf(3), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-01-20T23:00:00"), BigDecimal.valueOf(4), "NIGHT", BigDecimal.valueOf(10));
        // Different month - must not be merged in.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-02-10T09:00:00"), BigDecimal.valueOf(99), "DAY", BigDecimal.valueOf(20));

        List<UsageByMonthGroupByRateAndRateTypeProjection> results = usageRepository.findUsageByMonthGroupByRateAndRateType(
                mpan, LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-02-01T00:00:00"));

        assertThat(results).hasSize(2);
        UsageByMonthGroupByRateAndRateTypeProjection dayRow = results.stream()
                .filter(r -> r.getRateType().equals("DAY")).findFirst().orElseThrow();
        assertThat(dayRow.getUsageMonth()).isEqualTo(java.time.LocalDate.parse("2026-01-01"));
        assertThat(dayRow.getKwh()).isEqualByComparingTo("5.0000");

        UsageByMonthGroupByRateAndRateTypeProjection nightRow = results.stream()
                .filter(r -> r.getRateType().equals("NIGHT")).findFirst().orElseThrow();
        assertThat(nightRow.getKwh()).isEqualByComparingTo("4.0000");
    }

    @Test
    void byYear_aggregatesAcrossMonthsSeparatelyPerRateType() {
        String mpan = "7234567890123";
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, false, "ELEC"));
        Agreement agreement = agreementRepository.save(
                new Agreement("E-2R-DAY-NIGHT-TEST-Y", LocalDateTime.parse("2025-01-01T00:00:00"), null, meterPoint.getId()));

        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2025-03-05T09:00:00"), BigDecimal.valueOf(2), "DAY", BigDecimal.valueOf(20));
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2025-11-20T23:00:00"), BigDecimal.valueOf(3), "NIGHT", BigDecimal.valueOf(10));
        // Different year - must not be merged in.
        seedSlot(agreement.getId(), mpan, LocalDateTime.parse("2026-02-10T09:00:00"), BigDecimal.valueOf(99), "DAY", BigDecimal.valueOf(20));

        List<UsageByYearGroupByRateAndRateTypeProjection> results = usageRepository.findUsageByYearGroupByRateAndRateType(
                mpan, LocalDateTime.parse("2025-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T00:00:00"));

        assertThat(results).hasSize(2);
        UsageByYearGroupByRateAndRateTypeProjection dayRow = results.stream()
                .filter(r -> r.getRateType().equals("DAY")).findFirst().orElseThrow();
        assertThat(dayRow.getUsageYear()).isEqualTo(java.time.LocalDate.parse("2025-01-01"));
        assertThat(dayRow.getKwh()).isEqualByComparingTo("2.0000");

        UsageByYearGroupByRateAndRateTypeProjection nightRow = results.stream()
                .filter(r -> r.getRateType().equals("NIGHT")).findFirst().orElseThrow();
        assertThat(nightRow.getKwh()).isEqualByComparingTo("3.0000");
    }

    private void seedSlot(Long agreementId, String mpan, LocalDateTime slot, BigDecimal consumption, String rateType, BigDecimal valueIncVat) {
        unitRateByHalfHourRepository.save(new UnitRateByHalfHour(agreementId, valueIncVat, valueIncVat, slot, slot.plusMinutes(30), "DIRECT_DEBIT", rateType));
        usageRepository.save(new Usage(slot, slot.plusMinutes(30), consumption, mpan));
        utcToLocalRepository.save(new UtcToLocal(slot, slot, "GMT"));
    }
}
