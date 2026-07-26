package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "METER_POINT", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"mpan"})
})
public class MeterPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mpan", nullable = false)
    private String mpan;

    @Column(name = "is_export", nullable = false)
    private Boolean isExport;

    @Column(name = "meter_type", nullable = false)
    private String meterType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MeterPoint() {
        this.createdAt = LocalDateTime.now();
    }

    public MeterPoint(String mpan, Boolean isExport, String meterType) {
        this.mpan = mpan;
        this.isExport = isExport;
        this.meterType = meterType;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMpan() {
        return mpan;
    }

    public void setMpan(String mpan) {
        this.mpan = mpan;
    }

    public Boolean getIsExport() {
        return isExport;
    }

    public void setIsExport(Boolean isExport) {
        this.isExport = isExport;
    }

    public String getMeterType() {
        return meterType;
    }

    public void setMeterType(String meterType) {
        this.meterType = meterType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}