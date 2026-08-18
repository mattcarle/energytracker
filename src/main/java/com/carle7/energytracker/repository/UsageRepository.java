package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Usage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageRepository extends JpaRepository<Usage, Long>, UsageRepositoryCustom {

    Optional<Usage> findFirstByOrderByIntervalToDesc();

    // Called before re-inserting a freshly-fetched window of consumption data, so re-loading a
    // day already on record (see OctopusService#loadUsageData) replaces it rather than
    // duplicating it.
    @Transactional
    void deleteByMpanAndIntervalFromGreaterThanEqual(String mpan, LocalDateTime intervalFrom);

    // Called at the start of every data integrity check (see DataIntegrityService), so gap
    // detection runs against real readings only, and any placeholder rows it previously
    // inserted for a gap that's since been backfilled with real data don't linger forever.
    @Transactional
    void deleteByMissingTrue();

    @Query(value = """
            SELECT mpan AS mpan,
                   MIN(interval_from) AS earliest,
                   MAX(interval_to) AS latest
            FROM usage
            GROUP BY mpan
            """, nativeQuery = true)
    List<UsageDateRangeProjection> findDateRangeByMpan();

    @Query(value = """
            SELECT interval_from AS validFrom, interval_to AS validTo
            FROM usage
            WHERE mpan = :mpan
            ORDER BY interval_from
            """, nativeQuery = true)
    List<IntervalProjection> findDistinctIntervalsByMpan(@Param("mpan") String mpan);
}
