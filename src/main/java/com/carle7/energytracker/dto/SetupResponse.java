package com.carle7.energytracker.dto;

import com.carle7.energytracker.service.OctopusService.AccountLoadResult;
import com.carle7.energytracker.service.OctopusService.UsageLoadResult;

public class SetupResponse {

    private final UserResponse user;
    private final AccountLoadResult accountLoad;
    private final UsageLoadResult usageLoad;

    public SetupResponse(UserResponse user, AccountLoadResult accountLoad, UsageLoadResult usageLoad) {
        this.user = user;
        this.accountLoad = accountLoad;
        this.usageLoad = usageLoad;
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
}
