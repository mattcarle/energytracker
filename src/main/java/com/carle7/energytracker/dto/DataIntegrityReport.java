package com.carle7.energytracker.dto;

import java.util.List;

public class DataIntegrityReport {

    private final List<MpanIntegrityReport> mpans;

    public DataIntegrityReport(List<MpanIntegrityReport> mpans) {
        this.mpans = mpans;
    }

    public List<MpanIntegrityReport> getMpans() {
        return mpans;
    }
}
