package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.StandingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface StandingChargeRepository extends JpaRepository<StandingCharge, Long> {
    Optional<StandingCharge> findByAgreementIdAndPaymentMethodAndValidFrom(Long agreementId, String paymentMethod, LocalDateTime validFrom);
}
