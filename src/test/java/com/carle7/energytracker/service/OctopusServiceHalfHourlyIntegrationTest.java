package com.carle7.energytracker.service;

import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads a real export of AGREEMENT/UNIT_RATE/DAY_AND_NIGHT_TARIFF data (see
 * half-hourly-integration-data.sql) into the in-memory test database and runs the actual
 * populateHalfHourlyTariffData() method against it, to catch data-shaped edge cases that
 * hand-crafted unit tests miss.
 */
@SpringBootTest
@Transactional
class OctopusServiceHalfHourlyIntegrationTest {

    @Autowired
    private OctopusService octopusService;

    @Autowired
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Test
    @Sql("/half-hourly-integration-data.sql")
    void populateHalfHourlyUnitRates() {
        assertThat(agreementRepository.findAll()).isNotEmpty();

        octopusService.populateHalfHourlyUnitRates();

        List<UnitRateByHalfHour> slots = unitRateByHalfHourRepository.findAll();
        assertThat(slots).isNotEmpty();

        Set<String> seenKeys = new HashSet<>();
        for (UnitRateByHalfHour slot : slots) {
            String key = slot.getAgreementId() + "|" + slot.getValidFrom() + "|" + slot.getPaymentMethod() + "|" + slot.getRateType();
            assertThat(seenKeys.add(key)).as("duplicate half-hourly slot key: %s", key).isTrue();
        }
    }
}
