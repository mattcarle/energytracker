package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.SolarGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SolarGenerationRepository extends JpaRepository<SolarGeneration, Long> {

    Optional<SolarGeneration> findByPlantIdAndGenerationDate(String plantId, LocalDate generationDate);

    List<SolarGeneration> findByPlantIdAndGenerationDateGreaterThanEqualAndGenerationDateLessThanOrderByGenerationDateAsc(
            String plantId, LocalDate fromDate, LocalDate toDate);

    // Per-plant resume point for backfill - same shape/purpose as UsageRepository.findDateRangeByMpan,
    // so a future second plant would resume independently rather than sharing one cutover (see the
    // a1ce2d9 "lagging meter" fix this mirrors).
    @Query(value = """
            SELECT plant_id AS plantId,
                   MIN(generation_date) AS earliest,
                   MAX(generation_date) AS latest
            FROM solar_generation
            GROUP BY plant_id
            """, nativeQuery = true)
    List<SolarDateRangeProjection> findDateRangeByPlantId();

    // H2's DATE_TRUNC('WEEK', ...) truncates to the ISO-8601 week start (Monday), matching
    // Granularity.WEEK's expression used for Usage's own by-week view.
    @Query(value = """
            SELECT CAST(DATE_TRUNC('WEEK', generation_date) AS DATE) AS period,
                   SUM(energy_kwh) AS kwh
            FROM solar_generation
            WHERE plant_id = :plantId AND generation_date >= :fromDate AND generation_date < :toDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SolarByPeriodProjection> findByWeek(@Param("plantId") String plantId,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate);

    @Query(value = """
            SELECT CAST(DATE_TRUNC('MONTH', generation_date) AS DATE) AS period,
                   SUM(energy_kwh) AS kwh
            FROM solar_generation
            WHERE plant_id = :plantId AND generation_date >= :fromDate AND generation_date < :toDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SolarByPeriodProjection> findByMonth(@Param("plantId") String plantId,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate);

    @Query(value = """
            SELECT CAST(DATE_TRUNC('YEAR', generation_date) AS DATE) AS period,
                   SUM(energy_kwh) AS kwh
            FROM solar_generation
            WHERE plant_id = :plantId AND generation_date >= :fromDate AND generation_date < :toDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SolarByPeriodProjection> findByYear(@Param("plantId") String plantId,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate);
}
