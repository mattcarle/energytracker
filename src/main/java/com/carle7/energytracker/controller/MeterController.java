package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.repository.MeterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeterController {

    @Autowired
    private MeterRepository meterRepository;

    @GetMapping("/api/meters")
    public List<Meter> getAllMeters() {
        return meterRepository.findAll();
    }
}
