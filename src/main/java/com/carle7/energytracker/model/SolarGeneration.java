package com.carle7.energytracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SOLAR_GENERATION")
public class SolarGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private String plantId;

    @Column(name = "generation_date", nullable = false)
    private LocalDate generationDate;

    @Column(name = "energy_kwh", nullable = false)
    private BigDecimal energyKwh;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SolarGeneration() {
        this.createdAt = LocalDateTime.now();
    }

    public SolarGeneration(String plantId, LocalDate generationDate, BigDecimal energyKwh) {
        this.plantId = plantId;
        this.generationDate = generationDate;
        this.energyKwh = energyKwh;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlantId() {
        return plantId;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public LocalDate getGenerationDate() {
        return generationDate;
    }

    public void setGenerationDate(LocalDate generationDate) {
        this.generationDate = generationDate;
    }

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public void setEnergyKwh(BigDecimal energyKwh) {
        this.energyKwh = energyKwh;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
