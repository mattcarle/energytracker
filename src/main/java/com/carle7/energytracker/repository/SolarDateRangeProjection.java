package com.carle7.energytracker.repository;

import java.time.LocalDate;

public interface SolarDateRangeProjection {
    String getPlantId();
    LocalDate getEarliest();
    LocalDate getLatest();
}
