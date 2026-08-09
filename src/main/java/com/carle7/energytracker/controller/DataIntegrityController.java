package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.DataIntegrityReport;
import com.carle7.energytracker.service.DataIntegrityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataIntegrityController {

    @Autowired
    private DataIntegrityService dataIntegrityService;

    @GetMapping("/api/data-integrity/check")
    public DataIntegrityReport checkDataIntegrity() {
        return dataIntegrityService.checkDataIntegrity();
    }
}
