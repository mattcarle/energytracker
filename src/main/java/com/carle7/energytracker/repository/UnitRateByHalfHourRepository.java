package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.UnitRateByHalfHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRateByHalfHourRepository extends JpaRepository<UnitRateByHalfHour, Long> {
    Optional<UnitRateByHalfHour> findFirstByOrderByValidFromAsc();

    Optional<UnitRateByHalfHour> findFirstByOrderByValidFromDesc();

    @Query(value = """
            SELECT DISTINCT r.valid_from AS validFrom, r.valid_to AS validTo
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN unit_rate_by_half_hour r ON r.agreement_id = a.id
            WHERE mp.mpan = :mpan
            ORDER BY r.valid_from
            """, nativeQuery = true)
    List<IntervalProjection> findDistinctIntervalsByMpan(@Param("mpan") String mpan);
}