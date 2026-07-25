package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.MeterAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeterAgreementRepository extends JpaRepository<MeterAgreement, Long> {
    List<MeterAgreement> findByMeterId(Long meterId);
    List<MeterAgreement> findByAgreementId(Long agreementId);
}
