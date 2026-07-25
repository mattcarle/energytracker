package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "DAY_AND_NIGHT_TARIFF")
public class DayAndNightTariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tariff_code", nullable = false, unique = true)
    private String tariffCode;

    @Column(name = "night_rate_valid_from", nullable = false)
    private LocalTime nightRateValidFrom;

    @Column(name = "day_rate_valid_from", nullable = false)
    private LocalTime dayRateValidFrom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DayAndNightTariff() {
        this.createdAt = LocalDateTime.now();
    }

    public DayAndNightTariff(String tariffCode, LocalTime nightRateValidFrom, LocalTime dayRateValidFrom) {
        this.tariffCode = tariffCode;
        this.nightRateValidFrom = nightRateValidFrom;
        this.dayRateValidFrom = dayRateValidFrom;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public void setTariffCode(String tariffCode) {
        this.tariffCode = tariffCode;
    }

    public LocalTime getNightRateValidFrom() {
        return nightRateValidFrom;
    }

    public void setNightRateValidFrom(LocalTime nightRateValidFrom) {
        this.nightRateValidFrom = nightRateValidFrom;
    }

    public LocalTime getDayRateValidFrom() {
        return dayRateValidFrom;
    }

    public void setDayRateValidFrom(LocalTime dayRateValidFrom) {
        this.dayRateValidFrom = dayRateValidFrom;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
