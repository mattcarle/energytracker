package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "HAPPY_HOUR")
public class HappyHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDateTime validTo;

    // £ per kWh, not pence (unlike UnitRate/StandingCharge's valueIncVat) - matches how it's
    // entered in the UI; converted to pence at query time (see UsageRepositoryImpl) to combine
    // with the existing pence-based unit rate.
    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public HappyHour() {
        this.createdAt = LocalDateTime.now();
    }

    public HappyHour(LocalDateTime validFrom, LocalDateTime validTo, BigDecimal rate) {
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.rate = rate;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
