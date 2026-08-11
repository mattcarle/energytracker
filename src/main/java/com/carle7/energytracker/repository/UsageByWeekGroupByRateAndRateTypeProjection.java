package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByWeekGroupByRateAndRateTypeProjection extends UsageRateTypeProjection {
    LocalDate getUsageWeek();
}
