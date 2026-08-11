package com.carle7.energytracker.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Implemented by UsageRepositoryImpl. Same 6 method signatures UsageRepository exposed as
// @Query methods before this refactor, so UsageController and the repository tests need no
// changes - only how these rows get built moved, not the public contract.
interface UsageRepositoryCustom {

    List<UsageByHalfHourProjection> findUsageByHalfHour(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods);

    List<UsageByDayProjection> findUsageByDay(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods);

    List<UsageByMonthProjection> findUsageByMonth(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods);

    List<UsageByYearProjection> findUsageByYear(String mpan, LocalDate fromDate, LocalDate toDate, List<String> paymentMethods);

    List<UsageByHalfHourGroupByRateAndRateTypeProjection> findUsageByHalfHourGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo);

    List<UsageByDayGroupByRateAndRateTypeProjection> findUsageByDayGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo);

    List<UsageByMonthGroupByRateAndRateTypeProjection> findUsageByMonthGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo);

    List<UsageByYearGroupByRateAndRateTypeProjection> findUsageByYearGroupByRateAndRateType(String mpan, LocalDateTime intervalFrom, LocalDateTime intervalTo);
}
