package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AgreementRepository extends JpaRepository<Agreement, Long> {

    Optional<Agreement> findFirstByOrderByValidFromAsc();

    Optional<Agreement> findByMeterPointIdAndTariffCodeAndValidFrom(Long meterPointId, String tariffCode, LocalDateTime validFrom);
}
