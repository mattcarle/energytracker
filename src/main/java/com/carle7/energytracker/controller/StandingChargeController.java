package com.carle7.energytracker.controller;

import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.repository.StandingChargeByDayAggregateProjection;
import com.carle7.energytracker.repository.StandingChargeByDayRepository;
import com.carle7.energytracker.repository.StandingChargeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class StandingChargeController {

    private static final List<String> DEFAULT_PAYMENT_METHODS = List.of("DIRECT_DEBIT", "NA");

    @Autowired
    private StandingChargeRepository standingChargeRepository;

    @Autowired
    private StandingChargeByDayRepository standingChargeByDayRepository;

    @GetMapping("/api/standing-charges")
    public List<StandingCharge> getAllStandingCharges() {
        return standingChargeRepository.findAll();
    }

    @GetMapping("/api/standing-charges/by-day")
    public List<StandingChargeByDayAggregateProjection> getStandingChargesByDay(
            @RequestParam String mpan,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) List<String> paymentMethods) {

        LocalDate effectiveFromDate = fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveToDate = toDate != null ? toDate : LocalDate.now().plusDays(1);
        List<String> effectivePaymentMethods = paymentMethods != null && !paymentMethods.isEmpty() ? paymentMethods : DEFAULT_PAYMENT_METHODS;

        return standingChargeByDayRepository.findByMpanAndDateRange(mpan, effectiveFromDate, effectiveToDate, effectivePaymentMethods);
    }
}
