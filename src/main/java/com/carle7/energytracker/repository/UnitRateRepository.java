package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.UnitRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitRateRepository extends JpaRepository<UnitRate, Long> {
    List<UnitRate> findByAgreementIdOrderByValidFrom(Long agreementId);
}
