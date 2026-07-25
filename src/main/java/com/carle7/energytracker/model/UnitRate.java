package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "UNIT_RATE", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agreement_id", "valid_from"})
})
public class UnitRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Column(name = "value_exc_vat", nullable = false)
    private BigDecimal valueExcVat;

    @Column(name = "value_inc_vat", nullable = false)
    private BigDecimal valueIncVat;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "payment_method", nullable = true)
    private String paymentMethod;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UnitRate() {
        this.createdAt = LocalDateTime.now();
    }

    public UnitRate(Long agreementId, BigDecimal valueExcVat, BigDecimal valueIncVat, LocalDateTime validFrom, LocalDateTime validTo, String paymentMethod) {
        this.agreementId = agreementId;
        this.valueExcVat = valueExcVat;
        this.valueIncVat = valueIncVat;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.createdAt = LocalDateTime.now();
        this.paymentMethod = paymentMethod;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgreementId() { return agreementId; }
    public void setAgreementId(Long agreementId) { this.agreementId = agreementId; }

    public BigDecimal getValueExcVat() { return valueExcVat; }
    public void setValueExcVat(BigDecimal valueExcVat) { this.valueExcVat = valueExcVat; }

    public BigDecimal getValueIncVat() { return valueIncVat; }
    public void setValueIncVat(BigDecimal valueIncVat) { this.valueIncVat = valueIncVat; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
