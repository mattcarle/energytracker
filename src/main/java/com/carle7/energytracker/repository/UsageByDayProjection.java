package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByDayProjection extends UsageAggregateProjection {
    LocalDate getUsageDate();
}
