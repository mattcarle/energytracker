package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.DayAndNightTariffStatus;
import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.DayAndNightTariffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/day-and-night-tariffs")
public class DayAndNightTariffController {

    @Autowired
    private DayAndNightTariffRepository dayAndNightTariffRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @GetMapping
    public List<DayAndNightTariff> getAll() {
        return dayAndNightTariffRepository.findAll();
    }

    // Combines the tariff codes that actually have DAY/NIGHT unit rates (found by inspecting
    // agreements/unit rates, since the Octopus API gives no indication of which tariffs split
    // by time of day) with whatever valid-from times have been configured for them so far, so
    // the UI can show every tariff needing configuration alongside its current state.
    @GetMapping("/status")
    public List<DayAndNightTariffStatus> getStatus() {
        List<String> tariffCodes = agreementRepository.findTariffCodesRequiringDayAndNightRates();
        Map<String, DayAndNightTariff> existingByTariffCode = dayAndNightTariffRepository.findAll().stream()
                .collect(Collectors.toMap(DayAndNightTariff::getTariffCode, Function.identity()));

        return tariffCodes.stream()
                .map(tariffCode -> {
                    DayAndNightTariff existing = existingByTariffCode.get(tariffCode);
                    return existing == null
                            ? new DayAndNightTariffStatus(null, tariffCode, null, null)
                            : new DayAndNightTariffStatus(existing.getId(), tariffCode, existing.getDayRateValidFrom(), existing.getNightRateValidFrom());
                })
                .toList();
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
