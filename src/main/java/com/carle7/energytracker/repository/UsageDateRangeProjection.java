package com.carle7.energytracker.repository;

import java.time.LocalDateTime;

public interface UsageDateRangeProjection {
    String getMpan();
    LocalDateTime getEarliest();
    LocalDateTime getLatest();
}
