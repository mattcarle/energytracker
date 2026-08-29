package com.carle7.energytracker.dto;

import com.carle7.energytracker.service.GrowattService.PlantLoadResult;
import com.carle7.energytracker.service.GrowattService.SolarLoadResult;
import com.carle7.energytracker.service.OctopusService.AccountLoadResult;
import com.carle7.energytracker.service.OctopusService.UsageLoadResult;

public class SetupResponse {

    private final UserResponse user;
    private final AccountLoadResult accountLoad;
    private final UsageLoadResult usageLoad;
    private final DataIntegrityReport integrityReport;
    // Both null when the wizard's Growatt step was skipped - distinct from a populated result
    // whose own error field is set, which means it was attempted and failed.
    private final PlantLoadResult plantLoad;
    private final SolarLoadResult solarLoad;

    public SetupResponse(UserResponse user, AccountLoadResult accountLoad, UsageLoadResult usageLoad,
                          DataIntegrityReport integrityReport, PlantLoadResult plantLoad, SolarLoadResult solarLoad) {
        this.user = user;
        this.accountLoad = accountLoad;
        this.usageLoad = usageLoad;
        this.integrityReport = integrityReport;
        this.plantLoad = plantLoad;
        this.solarLoad = solarLoad;
    }

    public UserResponse getUser() {
        return user;
    }

    public AccountLoadResult getAccountLoad() {
        return accountLoad;
    }

    public UsageLoadResult getUsageLoad() {
        return usageLoad;
    }

    public DataIntegrityReport getIntegrityReport() {
        return integrityReport;
    }

    public PlantLoadResult getPlantLoad() {
        return plantLoad;
    }

    public SolarLoadResult getSolarLoad() {
        return solarLoad;
    }
}
