package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.HappyHour;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.model.UtcToLocal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// A happy-hour row is a time window with no meter point of its own, so the queries have to decide
// whose usage it applies to: only electricity import. Without that, gas and export usage that
// happened to fall inside the window was rated at the happy-hour rate too. Runs the real queries
// against H2 with one import, one export and one gas meter point all using 2 kWh in the same
// half hour, inside a happy hour.
@SpringBootTest
@Transactional
class UsageRepositoryHappyHourMeterPointTest {

    private static final String IMPORT_MPAN = "9100000000001";
    private static final String EXPORT_MPAN = "9100000000002";
    private static final String GAS_MPAN = "9100000000003";
    private static final LocalDateTime SLOT = LocalDateTime.parse("2026-01-05T09:00:00");
    private static final LocalDate DAY = LocalDate.parse("2026-01-05");
    private static final List<String> PAYMENT_METHODS = List.of("DIRECT_DEBIT");

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

    @Autowired
    private HappyHourRepository happyHourRepository;

    @BeforeEach
    void seed() {
        // One shared time mapping: the queries join through it, so a copy per meter point would
        // double every row.
        utcToLocalRepository.save(new UtcToLocal(SLOT, SLOT, "GMT"));

        // 5p/kWh (stored in pounds) across the half hour every meter point below used energy in.
        happyHourRepository.save(new HappyHour(SLOT.minusMinutes(30), SLOT.plusMinutes(60), new BigDecimal("0.05")));

        seedMeterPoint(IMPORT_MPAN, false, "ELEC");
        seedMeterPoint(EXPORT_MPAN, true, "ELEC");
        seedMeterPoint(GAS_MPAN, false, "GAS");
    }

    @Test
    void importUsageInAHappyHourIsRatedAtTheHappyHourRate() {
        UsageByDayProjection day = onlyDay(IMPORT_MPAN);

        // 2 kWh * 5p
        assertThat(day.getCost()).isEqualByComparingTo("0.10");
    }

    @Test
    void exportAndGasUsageInAHappyHourKeepTheirOrdinaryRate() {
        // 2 kWh * 20p
        assertThat(onlyDay(EXPORT_MPAN).getCost()).isEqualByComparingTo("0.40");
        assertThat(onlyDay(GAS_MPAN).getCost()).isEqualByComparingTo("0.40");
    }

    @Test
    void breakdownReportsAHappyHourRowOnlyForImport() {
        assertThat(breakdownRateTypes(IMPORT_MPAN)).containsExactly("HAPPY_HOUR");
        assertThat(breakdownRateTypes(EXPORT_MPAN)).containsExactly("STANDARD");
        assertThat(breakdownRateTypes(GAS_MPAN)).containsExactly("STANDARD");
    }

    @Test
    void happyHourSavingsAreOnlyEverCountedForImport() {
        HappyHourSavingsProjection importSavings = savings(IMPORT_MPAN);
        assertThat(importSavings.getKwh()).isEqualByComparingTo("2");
        // 2 kWh * (20p - 5p)
        assertThat(importSavings.getMoneySaved()).isEqualByComparingTo("0.30");

        for (String mpan : List.of(EXPORT_MPAN, GAS_MPAN)) {
            HappyHourSavingsProjection other = savings(mpan);
            assertThat(other.getKwh()).isEqualByComparingTo("0");
            assertThat(other.getMoneySaved()).isEqualByComparingTo("0");
        }
    }

    private void seedMeterPoint(String mpan, boolean isExport, String meterType) {
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint(mpan, isExport, meterType));
        Agreement agreement = agreementRepository.save(
                new Agreement("TARIFF-" + mpan, LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));
        unitRateByHalfHourRepository.save(new UnitRateByHalfHour(
                agreement.getId(), BigDecimal.valueOf(20), BigDecimal.valueOf(20), SLOT, SLOT.plusMinutes(30), "DIRECT_DEBIT", "STANDARD"));
        usageRepository.save(new Usage(SLOT, SLOT.plusMinutes(30), BigDecimal.valueOf(2), mpan));
    }

    private UsageByDayProjection onlyDay(String mpan) {
        List<UsageByDayProjection> days = usageRepository.findUsageByDay(mpan, DAY, DAY.plusDays(1), PAYMENT_METHODS);
        assertThat(days).hasSize(1);
        return days.get(0);
    }

    private List<String> breakdownRateTypes(String mpan) {
        return usageRepository.findUsageByDayGroupByRateAndRateType(mpan, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())
                .stream()
                .map(UsageByDayGroupByRateAndRateTypeProjection::getRateType)
                .toList();
    }

    private HappyHourSavingsProjection savings(String mpan) {
        return usageRepository.findHappyHourSavings(mpan, DAY, DAY.plusDays(1), PAYMENT_METHODS);
    }
}
