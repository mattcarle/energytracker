package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.MeterRepository;
import com.carle7.energytracker.repository.UsageRepository;
import com.carle7.energytracker.service.OctopusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UsageController {

    @Autowired
    private UsageRepository usageRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private com.carle7.energytracker.repository.StandingChargeRepository standingChargeRepository;

    @Autowired
    private OctopusService octopusService;

    @GetMapping("/api/usage")
    public List<Usage> getAllUsage() {
        return usageRepository.findAll();
    }

    @GetMapping(value = "/api/octopus/consumption", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getOctopusConsumption() {
        return octopusService.getConsumption();
    }

    @PostMapping("/api/usage/refresh")
    public void refreshUsageData() {
        octopusService.refreshData();
    }

    @GetMapping("/api/meters")
    public List<Meter> getAllMeters() {
        return meterRepository.findAll();
    }

    @GetMapping("/api/agreements")
    public List<Agreement> getAllAgreements() {
        return agreementRepository.findAll();
    }

    @GetMapping("/api/standing-charges")
    public java.util.List<com.carle7.energytracker.model.StandingCharge> getAllStandingCharges() {
        return standingChargeRepository.findAll();
    }

    @PostMapping("/api/octopus/account/load")
    public ResponseEntity<String> loadAccountDetails() {
        try {
            octopusService.loadAccountDetails();
            return ResponseEntity.ok("Account details loaded successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to load account details: " + e.getMessage());
        }
    }

    @GetMapping(value = "/api/octopus/account/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getAccountDetails() {
        return octopusService.getAccountDetails();
    }

    @PostMapping("/api/octopus/tariffs/half-hourly")
    public ResponseEntity<String> populateHalfHourlyTariffData() {
        try {
            octopusService.populateHalfHourlyTariffData();
            return ResponseEntity.ok("Half-hourly tariff data populated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to populate half-hourly tariff data: " + e.getMessage());
        }
    }
}
