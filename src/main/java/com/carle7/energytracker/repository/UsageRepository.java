package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Usage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageRepository extends JpaRepository<Usage, Long> {

    Optional<Usage> findFirstByOrderByIntervalToDesc();

    @Query(value = """
            SELECT mpan AS mpan,
                   MIN(interval_from) AS earliest,
                   MAX(interval_to) AS latest
            FROM usage
            GROUP BY mpan
            """, nativeQuery = true)
    List<UsageDateRangeProjection> findDateRangeByMpan();

    @Query(value = """
            SELECT mp.mpan AS mpan,
                   mp.meter_type AS meterType,
                   mp.is_export AS isExport,
                   CAST(z.local_time AS DATE) AS usageDate,
                   COUNT(*) AS intervalCount,
                   SUM(u.consumption) AS kwh,
                   SUM(u.consumption * r.value_inc_vat / 100) AS cost,
                   SUM(u.consumption * r.value_inc_vat / 100) / NULLIF(SUM(u.consumption), 0) AS avgRate
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN unit_rate_by_half_hour r ON r.valid_from = u.interval_from AND r.agreement_id = a.id
                     JOIN utc_to_local z ON u.interval_from = z.utc_time
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND r.payment_method IN (:paymentMethods)
            GROUP BY mp.mpan, mp.meter_type, mp.is_export, CAST(z.local_time AS DATE)
            ORDER BY CAST(z.local_time AS DATE)
            """, nativeQuery = true)
    List<UsageByDayProjection> findUsageByDay(@Param("mpan") String mpan,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate,
                                               @Param("paymentMethods") List<String> paymentMethods);

    @Query(value = """
            SELECT mp.mpan AS mpan,
                   mp.meter_type AS meterType,
                   mp.is_export AS isExport,
                   CAST(DATE_TRUNC('MONTH', z.local_time) AS DATE) AS usageMonth,
                   COUNT(*) AS intervalCount,
                   SUM(u.consumption) AS kwh,
                   SUM(u.consumption * r.value_inc_vat / 100) AS cost,
                   SUM(u.consumption * r.value_inc_vat / 100) / NULLIF(SUM(u.consumption), 0) AS avgRate
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN unit_rate_by_half_hour r ON r.valid_from = u.interval_from AND r.agreement_id = a.id
                     JOIN utc_to_local z ON u.interval_from = z.utc_time
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND r.payment_method IN (:paymentMethods)
            GROUP BY mp.mpan, mp.meter_type, mp.is_export, CAST(DATE_TRUNC('MONTH', z.local_time) AS DATE)
            ORDER BY CAST(DATE_TRUNC('MONTH', z.local_time) AS DATE)
            """, nativeQuery = true)
    List<UsageByMonthProjection> findUsageByMonth(@Param("mpan") String mpan,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate,
                                                   @Param("paymentMethods") List<String> paymentMethods);

    @Query(value = """
            SELECT mp.mpan AS mpan,
                   mp.meter_type AS meterType,
                   mp.is_export AS isExport,
                   CAST(DATE_TRUNC('YEAR', z.local_time) AS DATE) AS usageYear,
                   COUNT(*) AS intervalCount,
                   SUM(u.consumption) AS kwh,
                   SUM(u.consumption * r.value_inc_vat / 100) AS cost,
                   SUM(u.consumption * r.value_inc_vat / 100) / NULLIF(SUM(u.consumption), 0) AS avgRate
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN usage u ON mp.mpan = u.mpan
                     JOIN unit_rate_by_half_hour r ON r.valid_from = u.interval_from AND r.agreement_id = a.id
                     JOIN utc_to_local z ON u.interval_from = z.utc_time
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND r.payment_method IN (:paymentMethods)
            GROUP BY mp.mpan, mp.meter_type, mp.is_export, CAST(DATE_TRUNC('YEAR', z.local_time) AS DATE)
            ORDER BY CAST(DATE_TRUNC('YEAR', z.local_time) AS DATE)
            """, nativeQuery = true)
    List<UsageByYearProjection> findUsageByYear(@Param("mpan") String mpan,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate,
                                                 @Param("paymentMethods") List<String> paymentMethods);
}
