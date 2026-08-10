package com.carle7.energytracker.repository;

import java.math.BigDecimal;

public class RateBreakdown {

    private final String rateType;
    private final BigDecimal rate;
    private final BigDecimal kwh;

    public RateBreakdown(String rateType, BigDecimal rate, BigDecimal kwh) {
        this.rateType = rateType;
        this.rate = rate;
        this.kwh = kwh;
    }

    public String getRateType() {
        return rateType;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getKwh() {
        return kwh;
    }
}
