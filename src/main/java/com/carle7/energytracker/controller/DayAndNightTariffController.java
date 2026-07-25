package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.repository.DayAndNightTariffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/day-and-night-tariffs")
public class DayAndNightTariffController {

    @Autowired
    private DayAndNightTariffRepository dayAndNightTariffRepository;

    @GetMapping
    public List<DayAndNightTariff> getAll() {
        return dayAndNightTariffRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DayAndNightTariff> getById(@PathVariable Long id) {
        return dayAndNightTariffRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DayAndNightTariff> create(@RequestBody DayAndNightTariff tariff) {
        if (tariff.getId() != null) {
            return ResponseEntity.badRequest().build();
        }
        DayAndNightTariff saved = dayAndNightTariffRepository.save(tariff);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DayAndNightTariff> update(@PathVariable Long id, @RequestBody DayAndNightTariff tariff) {
        return dayAndNightTariffRepository.findById(id)
                .map(existing -> {
                    existing.setTariffCode(tariff.getTariffCode());
                    existing.setNightRateValidFrom(tariff.getNightRateValidFrom());
                    existing.setDayRateValidFrom(tariff.getDayRateValidFrom());
                    DayAndNightTariff updated = dayAndNightTariffRepository.save(existing);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!dayAndNightTariffRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        dayAndNightTariffRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
