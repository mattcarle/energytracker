package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.StandingChargeByDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StandingChargeByDayRepository extends JpaRepository<StandingChargeByDay, Long> {
}
