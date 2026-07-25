package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "METER_AGREEMENT", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"meter_id", "agreement_id"})
})
public class MeterAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_id", nullable = false)
    private Long meterId;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MeterAgreement() {
        this.createdAt = LocalDateTime.now();
    }

    public MeterAgreement(Long meterId, Long agreementId) {
        this.meterId = meterId;
        this.agreementId = agreementId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMeterId() {
        return meterId;
    }

    public void setMeterId(Long meterId) {
        this.meterId = meterId;
    }

    public Long getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(Long agreementId) {
        this.agreementId = agreementId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
