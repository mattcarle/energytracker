package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.DayAndNightTariff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DayAndNightTariffRepository extends JpaRepository<DayAndNightTariff, Long> {
    Optional<DayAndNightTariff> findByTariffCode(String tariffCode);
}
