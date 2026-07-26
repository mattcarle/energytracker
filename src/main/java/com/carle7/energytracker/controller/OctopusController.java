package com.carle7.energytracker.controller;

import com.carle7.energytracker.service.OctopusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OctopusController {

    @Autowired
    private OctopusService octopusService;

    @GetMapping(value = "/api/octopus/consumption", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getOctopusConsumption() {
        return octopusService.getConsumption();
    }

    @PostMapping("/api/octopus/account/load")
    public ResponseEntity<OctopusService.AccountLoadResult> loadAccountDetails() {
        OctopusService.AccountLoadResult result = octopusService.loadAccountDetails();
        if (result.getError() != null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/api/octopus/account/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getAccountDetails() {
        return octopusService.getAccountDetails();
    }

    @PostMapping("/api/octopus/tariffs/half-hourly")
    public ResponseEntity<String> populateHalfHourlyTariffData() {
        try {
            octopusService.populateHalfHourlyUnitRates();
            return ResponseEntity.ok("Half-hourly tariff data populated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to populate half-hourly tariff data: " + e.getMessage());
        }
    }
}
