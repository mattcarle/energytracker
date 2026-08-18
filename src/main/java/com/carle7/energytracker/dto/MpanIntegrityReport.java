package com.carle7.energytracker.dto;

public class MpanIntegrityReport {

    private final String mpan;
    private final String meterType;
    private final boolean isExport;
    private final IntegrityCheckResult agreements;
    private final IntegrityCheckResult standingCharges;
    private final IntegrityCheckResult unitRates;
    private final IntegrityCheckResult usage;

    public MpanIntegrityReport(String mpan, String meterType, boolean isExport,
                                IntegrityCheckResult agreements, IntegrityCheckResult standingCharges,
                                IntegrityCheckResult unitRates, IntegrityCheckResult usage) {
        this.mpan = mpan;
        this.meterType = meterType;
        this.isExport = isExport;
        this.agreements = agreements;
        this.standingCharges = standingCharges;
        this.unitRates = unitRates;
        this.usage = usage;
    }

    public String getMpan() {
        return mpan;
    }

    public String getMeterType() {
        return meterType;
    }

    public boolean getIsExport() {
        return isExport;
    }

    public IntegrityCheckResult getAgreements() {
        return agreements;
    }

    public IntegrityCheckResult getStandingCharges() {
        return standingCharges;
    }

    public IntegrityCheckResult getUnitRates() {
        return unitRates;
    }

    public IntegrityCheckResult getUsage() {
        return usage;
    }
}
