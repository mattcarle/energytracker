package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Usage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsageRepository extends JpaRepository<Usage, Long> {
}
