package com.carle7.energytracker.repository;

import java.math.BigDecimal;

public interface HappyHourSavingsProjection {
    BigDecimal getKwh();

    BigDecimal getMoneySaved();
}
