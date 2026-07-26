package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.UnitRateByHalfHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRateByHalfHourRepository extends JpaRepository<UnitRateByHalfHour, Long> {
}