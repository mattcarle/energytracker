package com.carle7.energytracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "UTC_TO_LOCAL")
public class UtcToLocal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utc_time", nullable = false)
    private LocalDateTime utcTime;

    @Column(name = "local_time", nullable = false)
    private LocalDateTime localTime;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UtcToLocal() {
        this.createdAt = LocalDateTime.now();
    }

    public UtcToLocal(LocalDateTime utcTime, LocalDateTime localTime, String timeZone) {
        this.utcTime = utcTime;
        this.localTime = localTime;
        this.timeZone = timeZone;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getUtcTime() { return utcTime; }
    public void setUtcTime(LocalDateTime utcTime) { this.utcTime = utcTime; }

    public LocalDateTime getLocalTime() { return localTime; }
    public void setLocalTime(LocalDateTime localTime) { this.localTime = localTime; }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "UtcToLocal{" +
                "id=" + id +
                ", utcTime=" + utcTime +
                ", localTime=" + localTime +
                ", timeZone='" + timeZone + '\'' +
                '}';
    }
}
