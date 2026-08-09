package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.UnitRate;
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
class AgreementRepositoryTest {

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private MeterPointRepository meterPointRepository;

    @Autowired
    private UnitRateRepository unitRateRepository;

    @Test
    void findTariffCodesRequiringDayAndNightRates_returnsOnlyTariffsWithDayOrNightRateTypes() {
        MeterPoint meterPoint = meterPointRepository.save(new MeterPoint("9234567890123", false, "ELEC"));

        Agreement dayNightAgreement = agreementRepository.save(
                new Agreement("E-2R-DAY-NIGHT-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));
        unitRateRepository.save(rate(dayNightAgreement.getId(), "DAY"));
        unitRateRepository.save(rate(dayNightAgreement.getId(), "NIGHT"));

        Agreement standardAgreement = agreementRepository.save(
                new Agreement("E-1R-STANDARD-TEST", LocalDateTime.parse("2026-01-01T00:00:00"), null, meterPoint.getId()));
        unitRateRepository.save(rate(standardAgreement.getId(), "STANDARD"));

        List<String> tariffCodes = agreementRepository.findTariffCodesRequiringDayAndNightRates();

        assertThat(tariffCodes).contains("E-2R-DAY-NIGHT-TEST");
        assertThat(tariffCodes).doesNotContain("E-1R-STANDARD-TEST");
    }

    private UnitRate rate(Long agreementId, String rateType) {
        return new UnitRate(agreementId, BigDecimal.TEN, BigDecimal.TEN,
                LocalDateTime.parse("2026-01-01T00:00:00"), null, "DIRECT_DEBIT", rateType);
    }
}
