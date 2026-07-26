package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "METER", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"meter_point_id", "serial_number"})
})
public class Meter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "meter_point_id", nullable = false)
    private Long meterPointId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Meter() {
        this.createdAt = LocalDateTime.now();
    }

    public Meter(String serialNumber, Long meterPointId) {
        this.serialNumber = serialNumber;
        this.meterPointId = meterPointId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Long getMeterPointId() {
        return meterPointId;
    }

    public void setMeterPointId(Long meterPointId) {
        this.meterPointId = meterPointId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
