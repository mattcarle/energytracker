package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.repository.AgreementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgreementController {

    @Autowired
    private AgreementRepository agreementRepository;

    @GetMapping("/api/agreements")
    public List<Agreement> getAllAgreements() {
        return agreementRepository.findAll();
    }
}
