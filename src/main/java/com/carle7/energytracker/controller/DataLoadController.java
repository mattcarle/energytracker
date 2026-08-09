package com.carle7.energytracker.controller;

import com.carle7.energytracker.service.OctopusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataLoadController {

    @Autowired
    private OctopusService octopusService;

    @PostMapping("/api/load/account")
    public ResponseEntity<OctopusService.AccountLoadResult> loadAccountData(
            @RequestParam(defaultValue = "false") boolean deleteAll) {
        // Always 200: the result carries its own error field, so a partial failure still
        // reports the counts that did succeed rather than losing them behind a 500.
        return ResponseEntity.ok(octopusService.loadAccountData(deleteAll));
    }

    @PostMapping("/api/load/usage")
    public ResponseEntity<OctopusService.UsageLoadResult> loadUsageData(
            @RequestParam(defaultValue = "false") boolean deleteAll) {
        return ResponseEntity.ok(octopusService.loadUsageData(deleteAll));
    }

    @PostMapping("/api/load/utc-to-local")
    public ResponseEntity<Integer> loadUtcToLocalMapping() {
        return ResponseEntity.ok(octopusService.populateUtcToLocalMapping());
    }

}
