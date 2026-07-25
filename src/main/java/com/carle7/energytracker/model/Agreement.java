package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AGREEMENT", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"meter_id", "tariff_code", "valid_from"})
})
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tariff_code", nullable = false)
    private String tariffCode;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "meter_id", nullable = false)
    private Long meterId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Agreement() {
        this.createdAt = LocalDateTime.now();
    }

    public Agreement(String tariffCode, LocalDateTime validFrom, LocalDateTime validTo, Long meterId) {
        this.tariffCode = tariffCode;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.meterId = meterId;
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

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public Long getMeterId() {
        return meterId;
    }

    public void setMeterId(Long meterId) {
        this.meterId = meterId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
