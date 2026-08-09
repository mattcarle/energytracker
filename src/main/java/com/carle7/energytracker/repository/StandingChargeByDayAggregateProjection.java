package com.carle7.energytracker.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface StandingChargeByDayAggregateProjection {
    LocalDate getChargeDate();
    BigDecimal getAmount();
}
