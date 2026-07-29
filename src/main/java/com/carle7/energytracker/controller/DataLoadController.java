package com.carle7.energytracker.controller;

import com.carle7.energytracker.service.OctopusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataLoadController {

    @Autowired
    private OctopusService octopusService;

    @PostMapping("/api/load/account")
    public ResponseEntity<OctopusService.AccountLoadResult> loadAccountData() {
        OctopusService.AccountLoadResult result = octopusService.loadAccountData();
        if (result.getError() != null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/load/usage")
    public ResponseEntity<OctopusService.UsageLoadResult> loadUsageData() {
        OctopusService.UsageLoadResult result = octopusService.loadUsageData();
        if (result.getError() != null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/load/utc-to-local")
    public ResponseEntity<Integer> loadUtcToLocalMapping() {
        return ResponseEntity.ok(octopusService.populateUtcToLocalMapping());
    }

}
