package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.MeterPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeterPointRepository extends JpaRepository<MeterPoint, Long> {
}
