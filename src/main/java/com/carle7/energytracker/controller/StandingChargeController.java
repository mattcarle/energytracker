package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.repository.StandingChargeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StandingChargeController {

    @Autowired
    private StandingChargeRepository standingChargeRepository;

    @GetMapping("/api/standing-charges")
    public List<StandingCharge> getAllStandingCharges() {
        return standingChargeRepository.findAll();
    }
}
