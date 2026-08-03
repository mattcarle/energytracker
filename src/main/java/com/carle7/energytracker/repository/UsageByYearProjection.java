package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByYearProjection extends UsageAggregateProjection {
    LocalDate getUsageYear();
}
