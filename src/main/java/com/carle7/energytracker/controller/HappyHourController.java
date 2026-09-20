package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.ErrorResponse;
import com.carle7.energytracker.model.HappyHour;
import com.carle7.energytracker.repository.HappyHourRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/happy-hours")
public class HappyHourController {

    private final HappyHourRepository happyHourRepository;

    public HappyHourController(HappyHourRepository happyHourRepository) {
        this.happyHourRepository = happyHourRepository;
    }

    @GetMapping
    public List<HappyHour> getAll() {
        return happyHourRepository.findAllByOrderByValidFromAsc();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody HappyHour happyHour) {
        if (happyHour.getId() != null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("New happy hour must not specify an id"));
        }
        if (happyHour.getValidFrom() == null || happyHour.getValidTo() == null || happyHour.getRate() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("From, To and Rate are all required"));
        }
        if (!happyHour.getValidTo().isAfter(happyHour.getValidFrom())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("To must be after From"));
        }
        if (happyHour.getRate().signum() < 0) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Rate cannot be negative"));
        }
        // Half-hours are rate-joined against HAPPY_HOUR with a plain LEFT JOIN (see
        // UsageRepositoryImpl) rather than a de-duplicating subquery, so two overlapping rows
        // would silently double-count consumption for the overlap - rejected here instead so
        // that join never has more than one candidate row to match against a given instant.
        boolean overlaps = happyHourRepository.findAll().stream()
                .anyMatch(existing -> happyHour.getValidFrom().isBefore(existing.getValidTo())
                        && happyHour.getValidTo().isAfter(existing.getValidFrom()));
        if (overlaps) {
            return ResponseEntity.badRequest().body(new ErrorResponse("This period overlaps an existing happy hour"));
        }
        HappyHour saved = happyHourRepository.save(happyHour);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!happyHourRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        happyHourRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
