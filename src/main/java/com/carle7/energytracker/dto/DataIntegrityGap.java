package com.carle7.energytracker.dto;

import java.time.LocalDateTime;

/**
 * A break in an otherwise contiguous sequence: the end of one record didn't line up with the
 * start of the next. Usually a gap (from is before to) but could also flag an overlap.
 */
public class DataIntegrityGap {

    private final LocalDateTime from;
    private final LocalDateTime to;

    public DataIntegrityGap(LocalDateTime from, LocalDateTime to) {
        this.from = from;
        this.to = to;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public LocalDateTime getTo() {
        return to;
    }
}
