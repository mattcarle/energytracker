package com.carle7.energytracker.repository;

import java.math.BigDecimal;

public interface UsageAggregateProjection {
    String getMpan();

    String getMeterType();

    Boolean getIsExport();

    Long getIntervalCount();

    BigDecimal getKwh();

    BigDecimal getCost();

    BigDecimal getAvgRate();
}
