package com.carle7.energytracker.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public interface UsageAggregateProjection {

    BigDecimal TWO = BigDecimal.valueOf(2);
    // Breakdown rows carry the raw pence-per-kWh rate (unit rate straight from Octopus), while
    // getCost()/getAvgRate() are already converted to pounds - match that conversion here too.
    BigDecimal HUNDRED = BigDecimal.valueOf(100);

    String getMpan();

    String getMeterType();

    Boolean getIsExport();

    Long getIntervalCount();

    // How many of getIntervalCount()'s intervals are placeholder rows the data integrity check
    // inserted for a half-hour Octopus never reported (see DataIntegrityService), rather than
    // real readings - lets callers tell a period with genuine zero consumption apart from one
    // that's simply missing data.
    Long getMissingIntervalCount();

    BigDecimal getKwh();

    BigDecimal getCost();

    BigDecimal getAvgRate();

    List<RateBreakdown> getBreakdown();

    // Off-peak usage isn't a stored value - it's derived from the breakdown. A single rate for
    // the period means the tariff has no peak/off-peak split, so there's nothing to derive.
    // NIGHT/DAY breakdown rows are unambiguous. A STANDARD tariff with multiple rates could
    // either be a genuine peak/off-peak split or just a single rate that changed mid-period;
    // we treat it as a split only when the cheaper rate is less than half the pricier one.
    default BigDecimal getKwhOffPeak() {
        List<RateBreakdown> offPeakRows = offPeakRows();
        if (offPeakRows == null) {
            return null;
        }
        return offPeakRows.stream().map(RateBreakdown::getKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Same off-peak/peak split as getKwhOffPeak(), but summing rate*kwh instead of just kwh -
    // off-peak and peak rates differ, so this isn't just a proportional share of getCost().
    default BigDecimal getCostOffPeak() {
        List<RateBreakdown> offPeakRows = offPeakRows();
        if (offPeakRows == null) {
            return null;
        }
        return offPeakRows.stream()
                .map(row -> row.getRate().multiply(row.getKwh()).divide(HUNDRED))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RateBreakdown> offPeakRows() {
        List<RateBreakdown> breakdown = getBreakdown();
        if (breakdown == null || breakdown.size() < 2) {
            return null;
        }

        BigDecimal minRate = breakdown.stream().map(RateBreakdown::getRate).min(BigDecimal::compareTo).orElse(null);
        BigDecimal maxRate = breakdown.stream().map(RateBreakdown::getRate).max(BigDecimal::compareTo).orElse(null);
        if (minRate.multiply(TWO).compareTo(maxRate) >= 0) {
            return null;
        }

        // DAY rates always peak, NIGHT rates always off-peak, otherwise treat any rate that is less than half the maximum rate as off-peak
        return breakdown.stream()
                .filter(row ->
                        !"DAY".equals(row.getRateType()) &&
                        ("NIGHT".equals(row.getRateType()) || row.getRate().multiply(TWO).compareTo(maxRate) < 0))
                .collect(Collectors.toList());
    }
}
