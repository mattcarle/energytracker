package com.carle7.energytracker.dto;

import com.carle7.energytracker.service.OctopusService.AccountLoadResult;
import com.carle7.energytracker.service.OctopusService.UsageLoadResult;

public class SetupResponse {

    private final UserResponse user;
    private final AccountLoadResult accountLoad;
    private final UsageLoadResult usageLoad;
    private final DataIntegrityReport integrityReport;

    public SetupResponse(UserResponse user, AccountLoadResult accountLoad, UsageLoadResult usageLoad,
                          DataIntegrityReport integrityReport) {
        this.user = user;
        this.accountLoad = accountLoad;
        this.usageLoad = usageLoad;
        this.integrityReport = integrityReport;
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
}
