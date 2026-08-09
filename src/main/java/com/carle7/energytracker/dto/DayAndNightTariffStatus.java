package com.carle7.energytracker.dto;

import java.time.LocalTime;

public class DayAndNightTariffStatus {

    private final Long id;
    private final String tariffCode;
    private final LocalTime dayRateValidFrom;
    private final LocalTime nightRateValidFrom;

    public DayAndNightTariffStatus(Long id, String tariffCode, LocalTime dayRateValidFrom, LocalTime nightRateValidFrom) {
        this.id = id;
        this.tariffCode = tariffCode;
        this.dayRateValidFrom = dayRateValidFrom;
        this.nightRateValidFrom = nightRateValidFrom;
    }

    public Long getId() {
        return id;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public LocalTime getDayRateValidFrom() {
        return dayRateValidFrom;
    }

    public LocalTime getNightRateValidFrom() {
        return nightRateValidFrom;
    }
}
