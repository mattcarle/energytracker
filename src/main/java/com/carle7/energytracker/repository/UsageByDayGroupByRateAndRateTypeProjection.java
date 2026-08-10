package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface UsageByDayGroupByRateAndRateTypeProjection extends UsageRateTypeProjection {
    LocalDate getUsageDate();
}
