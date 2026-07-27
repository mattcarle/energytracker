package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "USAGE")
public class Usage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interval_from", nullable = false)
    private LocalDateTime intervalFrom;

    @Column(name = "interval_to", nullable = false)
    private LocalDateTime intervalTo;

    @Column(name = "consumption", nullable = false)
    private BigDecimal consumption;

    @Column(name = "mpan", nullable = false)
    private String mpan;

    public Usage() {}

    public Usage(LocalDateTime intervalFrom, LocalDateTime intervalTo, BigDecimal consumption, String mpan) {
        this.intervalFrom = intervalFrom;
        this.intervalTo = intervalTo;
        this.consumption = consumption;
        this.mpan = mpan;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getIntervalFrom() {
        return intervalFrom;
    }

    public void setIntervalFrom(LocalDateTime intervalFrom) {
        this.intervalFrom = intervalFrom;
    }

    public LocalDateTime getIntervalTo() {
        return intervalTo;
    }

    public void setIntervalTo(LocalDateTime intervalTo) {
        this.intervalTo = intervalTo;
    }

    public BigDecimal getConsumption() {
        return consumption;
    }

    public void setConsumption(BigDecimal consumption) {
        this.consumption = consumption;
    }

    public String getMpan() {
        return mpan;
    }

    public void setMpan(String mpan) {
        this.mpan = mpan;
    }
}
