package com.carle7.energytracker.service;

import com.carle7.energytracker.dto.DataIntegrityGap;
import com.carle7.energytracker.dto.DataIntegrityReport;
import com.carle7.energytracker.dto.IntegrityCheckResult;
import com.carle7.energytracker.dto.MpanIntegrityReport;
import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.IntervalProjection;
import com.carle7.energytracker.repository.MeterPointRepository;
import com.carle7.energytracker.repository.StandingChargeByDayRepository;
import com.carle7.energytracker.repository.UnitRateByHalfHourRepository;
import com.carle7.energytracker.repository.UsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    @Autowired
    private UsageRepository usageRepository;

    public DataIntegrityReport checkDataIntegrity() {
        List<MpanIntegrityReport> reports = new ArrayList<>();

        // Wipe last run's placeholders before scanning, so gap detection sees real readings
        // only - any gap that's since been backfilled with real data doesn't get re-filled
        // with a stale placeholder, and any gap that's still open gets a fresh one below.
        usageRepository.deleteByMissingTrue();

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

            IntegrityCheckResult usageResult = checkContiguity(
                    normalizeUsageIntervals(
                            usageRepository.findDistinctIntervalsByMpan(meterPoint.getMpan()).stream()
                                    .map(Interval::from)
                                    .toList()));
            fillMissingUsage(meterPoint.getMpan(), usageResult.getGaps());

            reports.add(new MpanIntegrityReport(
                    meterPoint.getMpan(),
                    meterPoint.getMeterType(),
                    Boolean.TRUE.equals(meterPoint.getIsExport()),
                    agreementsResult,
                    standingChargeResult,
                    unitRateResult,
                    usageResult
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
     * Usage readings are always exactly 30 minutes long, but (unlike agreements/standing
     * charges/unit rates, which are keyed by true UTC instants) are stored as bare local time,
     * matching how Octopus itself reports them and how day/week/month aggregation joins against
     * them elsewhere. That's fine for 364 days a year, but on the UK's autumn clock-change day
     * the local hour 01:00-01:59 happens twice, which breaks naive local-time ordering two ways:
     * - the one real reading whose 30 minutes span the clock change itself ends up with a
     *   validTo that reads earlier than its validFrom (e.g. 01:30 -> 01:00), since that's the
     *   correct local-time label for the instant it ends at - not a data error;
     * - the two genuinely repeated half-hours (once before the clocks change, once after) are
     *   stored as identical (validFrom, validTo) pairs, which sort adjacent to each other and
     *   register as a spurious round-trip gap between "duplicate" entries that are actually two
     *   distinct, real readings.
     * Both are present data, not missing data, so they're straightened/collapsed here - for gap
     * detection only, never touching what's actually stored - before the generic scan below,
     * which would otherwise misreport real readings as gaps purely from this once-a-year local
     * time ambiguity.
     */
    private List<Interval> normalizeUsageIntervals(List<Interval> intervals) {
        List<Interval> normalized = new ArrayList<>();
        for (Interval interval : intervals) {
            Interval straightened = interval.validTo().isBefore(interval.validFrom())
                    ? new Interval(interval.validFrom(), interval.validFrom().plusMinutes(30))
                    : interval;
            if (normalized.isEmpty() || !normalized.get(normalized.size() - 1).equals(straightened)) {
                normalized.add(straightened);
            }
        }
        return normalized;
    }

    /**
     * Backfills every half-hour usage gap just found with a placeholder row (consumption zero,
     * missing = true), so charts and aggregations elsewhere in the app see a complete, contiguous
     * series instead of silently-absent intervals. Wiped and recomputed fresh on every check (see
     * checkDataIntegrity) rather than being a one-off fix, so a gap that gets backfilled with real
     * data later (e.g. via Refresh Usage Data) naturally stops being recreated once it's no longer
     * a real gap.
     */
    private void fillMissingUsage(String mpan, List<DataIntegrityGap> gaps) {
        List<Usage> placeholders = new ArrayList<>();
        for (DataIntegrityGap gap : gaps) {
            if (gap.getFrom() == null || gap.getTo() == null) {
                continue;
            }
            for (LocalDateTime slotStart = gap.getFrom(); slotStart.isBefore(gap.getTo()); slotStart = slotStart.plusMinutes(30)) {
                placeholders.add(new Usage(slotStart, slotStart.plusMinutes(30), BigDecimal.ZERO, mpan, true));
            }
        }
        if (!placeholders.isEmpty()) {
            usageRepository.saveAll(placeholders);
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
