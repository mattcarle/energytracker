package com.carle7.energytracker.service;

import com.carle7.energytracker.dto.DataIntegrityGap;
import com.carle7.energytracker.dto.DataIntegrityReport;
import com.carle7.energytracker.dto.IntegrityCheckResult;
import com.carle7.energytracker.dto.MpanIntegrityReport;
import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.IntervalProjection;
import com.carle7.energytracker.repository.MeterPointRepository;
import com.carle7.energytracker.repository.StandingChargeByDayRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataIntegrityService {

    @Autowired
    private MeterPointRepository meterPointRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private StandingChargeByDayRepository standingChargeByDayRepository;

    @Autowired
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    public DataIntegrityReport checkDataIntegrity() {
        List<MpanIntegrityReport> reports = new ArrayList<>();

        for (MeterPoint meterPoint : meterPointRepository.findAll()) {
            List<Agreement> agreements = agreementRepository.findByMeterPointIdOrderByValidFrom(meterPoint.getId());
            IntegrityCheckResult agreementsResult = checkContiguity(agreements.stream()
                    .map(a -> new Interval(a.getValidFrom(), a.getValidTo()))
                    .toList());

            IntegrityCheckResult standingChargeResult = checkContiguity(
                    standingChargeByDayRepository.findDistinctIntervalsByMpan(meterPoint.getMpan()).stream()
                            .map(Interval::from)
                            .toList());

            IntegrityCheckResult unitRateResult = checkContiguity(
                    unitRateByHalfHourRepository.findDistinctIntervalsByMpan(meterPoint.getMpan()).stream()
                            .map(Interval::from)
                            .toList());

            reports.add(new MpanIntegrityReport(
                    meterPoint.getMpan(),
                    meterPoint.getMeterType(),
                    Boolean.TRUE.equals(meterPoint.getIsExport()),
                    agreementsResult,
                    standingChargeResult,
                    unitRateResult
            ));
        }

        return new DataIntegrityReport(reports);
    }

    private record Interval(LocalDateTime validFrom, LocalDateTime validTo) {
        static Interval from(IntervalProjection projection) {
            return new Interval(projection.getValidFrom(), projection.getValidTo());
        }
    }

    /**
     * Sorted, contiguous data has each record's valid_to equal to the next record's valid_from.
     * Any place that isn't true - a gap, an overlap, or a null valid_to before the last record -
     * gets reported.
     */
    private IntegrityCheckResult checkContiguity(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            return new IntegrityCheckResult(null, null, List.of());
        }

        LocalDateTime earliest = intervals.get(0).validFrom();
        LocalDateTime latest = intervals.get(intervals.size() - 1).validTo();

        List<DataIntegrityGap> gaps = new ArrayList<>();
        for (int i = 0; i < intervals.size() - 1; i++) {
            LocalDateTime endOfCurrent = intervals.get(i).validTo();
            LocalDateTime startOfNext = intervals.get(i + 1).validFrom();
            if (endOfCurrent == null || !endOfCurrent.equals(startOfNext)) {
                gaps.add(new DataIntegrityGap(endOfCurrent, startOfNext));
            }
        }

        return new IntegrityCheckResult(earliest, latest, gaps);
    }
}
