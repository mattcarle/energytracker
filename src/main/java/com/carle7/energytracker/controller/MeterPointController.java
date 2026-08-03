package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.repository.MeterPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeterPointController {

    @Autowired
    private MeterPointRepository meterPointRepository;

    @GetMapping("/api/meter-points")
    public List<MeterPoint> getAllMeterPoints() {
        return meterPointRepository.findAll();
    }
}
