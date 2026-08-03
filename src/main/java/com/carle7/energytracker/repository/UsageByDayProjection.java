package com.carle7.energytracker.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface UsageByDayProjection {
    String getMpan();

    String getMeterType();

    Boolean getIsExport();

    LocalDate getUsageDate();

    Long getIntervalCount();

    BigDecimal getKwh();

    BigDecimal getCost();

    BigDecimal getAvgRate();
}
