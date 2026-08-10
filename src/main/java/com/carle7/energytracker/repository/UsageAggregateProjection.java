package com.carle7.energytracker.repository;

import java.math.BigDecimal;
import java.util.List;

public interface UsageAggregateProjection {

    BigDecimal TWO = BigDecimal.valueOf(2);

    String getMpan();

    String getMeterType();

    Boolean getIsExport();

    Long getIntervalCount();

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
                .map(RateBreakdown::getKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
