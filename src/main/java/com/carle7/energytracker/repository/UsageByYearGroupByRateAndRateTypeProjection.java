package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByYearGroupByRateAndRateTypeProjection extends UsageRateTypeProjection {
    LocalDate getUsageYear();
}
