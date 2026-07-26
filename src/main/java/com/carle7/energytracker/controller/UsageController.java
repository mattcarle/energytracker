package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.UsageRepository;
import com.carle7.energytracker.service.OctopusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UsageController {

    @Autowired
    private UsageRepository usageRepository;

    @Autowired
    private OctopusService octopusService;

    @GetMapping("/api/usage")
    public List<Usage> getAllUsage() {
        return usageRepository.findAll();
    }

    @PostMapping("/api/usage/refresh")
    public void refreshUsageData() {
        octopusService.refreshData();
    }
}
