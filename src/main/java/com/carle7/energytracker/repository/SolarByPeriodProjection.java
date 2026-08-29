package com.carle7.energytracker.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

// Reused for the week/month/year aggregations - only the DATE_TRUNC grain in the query differs,
// not the shape of a row (a period start date + its summed kWh).
public interface SolarByPeriodProjection {
    LocalDate getPeriod();
    BigDecimal getKwh();
}
