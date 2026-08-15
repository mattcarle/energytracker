package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.DayAndNightTariffStatus;
import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.DayAndNightTariffRepository;
import com.carle7.energytracker.service.OctopusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/day-and-night-tariffs")
public class DayAndNightTariffController {

    @Autowired
    private DayAndNightTariffRepository dayAndNightTariffRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private OctopusService octopusService;

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
        recalculateHalfHourlyRatesIfComplete();
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
                    recalculateHalfHourlyRatesIfComplete();
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

    // UNIT_RATE_BY_HALF_HOUR is derived from unit_rate plus each tariff's configured Day/Night
    // valid-from times (see OctopusService.populateHalfHourlyUnitRates), so a tariff saved here
    // doesn't take effect there until it's recomputed. Only worth doing once every tariff that
    // needs Day/Night rates has one configured - recomputing on a still-incomplete save would
    // just repeat the same "no time boundary yet" fallback loadAccountData already left behind.
    private void recalculateHalfHourlyRatesIfComplete() {
        List<String> tariffCodesRequiringSetup = agreementRepository.findTariffCodesRequiringDayAndNightRates();
        Set<String> configuredTariffCodes = dayAndNightTariffRepository.findAll().stream()
                .map(DayAndNightTariff::getTariffCode)
                .collect(Collectors.toSet());
        if (configuredTariffCodes.containsAll(tariffCodesRequiringSetup)) {
            octopusService.populateHalfHourlyUnitRates();
        }
    }
}
