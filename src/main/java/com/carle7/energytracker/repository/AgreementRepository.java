package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementRepository extends JpaRepository<Agreement, Long> {

    Optional<Agreement> findFirstByOrderByValidFromAsc();

    Optional<Agreement> findByMeterPointIdAndTariffCodeAndValidFrom(Long meterPointId, String tariffCode, LocalDateTime validFrom);

    List<Agreement> findByMeterPointIdOrderByValidFrom(Long meterPointId);

    @Query(value = """
            SELECT DISTINCT a.tariff_code
            FROM agreement a
                     JOIN unit_rate r ON a.id = r.agreement_id
            WHERE r.rate_type IN ('DAY', 'NIGHT')
            ORDER BY a.tariff_code
            """, nativeQuery = true)
    List<String> findTariffCodesRequiringDayAndNightRates();
}
