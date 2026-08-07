package com.carle7.energytracker.dto;

public class SetupRequest {

    private String password;
    private String octopusAccountNumber;
    private String octopusAuthToken;

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
}
