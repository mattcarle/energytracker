package com.carle7.energytracker.repository;

import java.math.BigDecimal;

public interface UsageRateTypeProjection {
    String getMpan();

    String getRateType();

    BigDecimal getRate();

    BigDecimal getKwh();
}
