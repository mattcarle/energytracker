package com.carle7.energytracker.repository;

import java.time.LocalDateTime;

public interface UsageByHalfHourGroupByRateAndRateTypeProjection extends UsageRateTypeProjection {
    LocalDateTime getUsageInterval();
}
