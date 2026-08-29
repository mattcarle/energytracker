package com.carle7.energytracker.dto;

public class SetupRequest {

    private String password;
    private String octopusAccountNumber;
    private String octopusAuthToken;
    // Optional - the wizard's Growatt step can be skipped, in which case this is null/blank and
    // no Growatt credentials are saved.
    private String growattApiToken;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOctopusAccountNumber() {
        return octopusAccountNumber;
    }

    public void setOctopusAccountNumber(String octopusAccountNumber) {
        this.octopusAccountNumber = octopusAccountNumber;
    }

    public String getOctopusAuthToken() {
        return octopusAuthToken;
    }

    public void setOctopusAuthToken(String octopusAuthToken) {
        this.octopusAuthToken = octopusAuthToken;
    }

    public String getGrowattApiToken() {
        return growattApiToken;
    }

    public void setGrowattApiToken(String growattApiToken) {
        this.growattApiToken = growattApiToken;
    }
}
