package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.StandingChargeByDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StandingChargeByDayRepository extends JpaRepository<StandingChargeByDay, Long> {

    @Query(value = """
            SELECT CAST(z.local_time AS DATE) AS chargeDate,
                   SUM(sc.value_inc_vat) / 100 AS amount
            FROM meter_point mp
                     JOIN agreement a ON mp.id = a.meter_point_id
                     JOIN standing_charge_by_day sc ON sc.agreement_id = a.id
                     JOIN utc_to_local z ON sc.valid_from = z.utc_time
            WHERE mp.mpan = :mpan
              AND z.local_time >= :fromDate
              AND z.local_time < :toDate
              AND sc.payment_method IN (:paymentMethods)
            GROUP BY CAST(z.local_time AS DATE)
            ORDER BY CAST(z.local_time AS DATE)
            """, nativeQuery = true)
    List<StandingChargeByDayAggregateProjection> findByMpanAndDateRange(@Param("mpan") String mpan,
                                                                         @Param("fromDate") LocalDate fromDate,
                                                                         @Param("toDate") LocalDate toDate,
                                                                         @Param("paymentMethods") List<String> paymentMethods);
}
