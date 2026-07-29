package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.UnitRateByHalfHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitRateByHalfHourRepository extends JpaRepository<UnitRateByHalfHour, Long> {
    Optional<UnitRateByHalfHour> findFirstByOrderByValidFromAsc();

    Optional<UnitRateByHalfHour> findFirstByOrderByValidFromDesc();
}