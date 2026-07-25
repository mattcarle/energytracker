package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeterRepository extends JpaRepository<Meter, Long> {
    Optional<Meter> findByMpanAndSerialNumber(String mpan, String serialNumber);
}
