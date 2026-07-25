package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "METER", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"mpan", "serial_number"})
})
public class Meter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mpan", nullable = false)
    private String mpan;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "is_export", nullable = false)
    private Boolean isExport;

    @Column(name = "meter_type", nullable = false)
    private String meterType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Meter() {
        this.createdAt = LocalDateTime.now();
    }

    public Meter(String mpan, String serialNumber, Boolean isExport, String meterType) {
        this.mpan = mpan;
        this.serialNumber = serialNumber;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
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
