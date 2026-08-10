package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByMonthGroupByRateAndRateTypeProjection extends UsageRateTypeProjection {
    LocalDate getUsageMonth();
}
