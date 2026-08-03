package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByMonthProjection extends UsageAggregateProjection {
    LocalDate getUsageMonth();
}
