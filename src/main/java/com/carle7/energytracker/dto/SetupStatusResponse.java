package com.carle7.energytracker.dto;

public class SetupStatusResponse {

    private final boolean setupRequired;

    public SetupStatusResponse(boolean setupRequired) {
        this.setupRequired = setupRequired;
    }

    public boolean isSetupRequired() {
        return setupRequired;
    }
}
