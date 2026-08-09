package com.carle7.energytracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public class IntegrityCheckResult {

    private final LocalDateTime earliest;
    private final LocalDateTime latest;
    private final List<DataIntegrityGap> gaps;

    public IntegrityCheckResult(LocalDateTime earliest, LocalDateTime latest, List<DataIntegrityGap> gaps) {
        this.earliest = earliest;
        this.latest = latest;
        this.gaps = gaps;
    }

    public LocalDateTime getEarliest() {
        return earliest;
    }

    public LocalDateTime getLatest() {
        return latest;
    }

    public List<DataIntegrityGap> getGaps() {
        return gaps;
    }
}
