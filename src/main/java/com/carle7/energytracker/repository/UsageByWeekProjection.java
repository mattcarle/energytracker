package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByWeekProjection extends UsageAggregateProjection {
    LocalDate getUsageWeek();
}
