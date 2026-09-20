package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.HappyHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HappyHourRepository extends JpaRepository<HappyHour, Long> {
    List<HappyHour> findAllByOrderByValidFromAsc();
}
