package com.carle7.energytracker.repository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Happy-hour usage isn't part of the tariff's own peak/off-peak structure, so it's reported
// separately (getKwhHappyHour, its own bucket beside off-peak and peak) and never counted as
// off-peak or as evidence of a peak/off-peak split.
class UsageAggregateProjectionHappyHourTest {

    private static RateBreakdown row(String rateType, String ratePence, String kwh) {
        return new RateBreakdown(rateType, new BigDecimal(ratePence), new BigDecimal(kwh));
    }

    private static UsageAggregateProjection projectionOf(RateBreakdown... rows) {
        List<RateBreakdown> breakdown = List.of(rows);
        return new UsageAggregateProjection() {
            @Override
            public String getMpan() {
                return "mpan";
            }

            @Override
            public String getMeterType() {
                return "ELECTRICITY";
            }

            @Override
            public Boolean getIsExport() {
                return false;
            }

            @Override
            public Long getIntervalCount() {
                return 0L;
            }

            @Override
            public Long getMissingIntervalCount() {
                return 0L;
            }

            @Override
            public BigDecimal getKwh() {
                return breakdown.stream().map(RateBreakdown::getKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            @Override
            public BigDecimal getCost() {
                return BigDecimal.ZERO;
            }

            @Override
            public BigDecimal getAvgRate() {
                return null;
            }

            @Override
            public List<RateBreakdown> getBreakdown() {
                return breakdown;
            }
        };
    }

    @Test
    void happyHourKwhIsSummedAcrossEveryHappyHourRow() {
        UsageAggregateProjection period = projectionOf(
                row("NIGHT", "7.5", "10"),
                row("DAY", "30", "20"),
                row("HAPPY_HOUR", "0", "3"),
                row("HAPPY_HOUR", "10", "2"));

        assertThat(period.getKwhHappyHour()).isEqualByComparingTo("5");
    }

    @Test
    void happyHourCostIsRateTimesKwhInPoundsSummedAcrossHappyHourRows() {
        UsageAggregateProjection period = projectionOf(
                row("NIGHT", "7.5", "10"),
                row("HAPPY_HOUR", "0", "3"),
                row("HAPPY_HOUR", "10", "2"));

        // 0p * 3 kWh + 10p * 2 kWh = 20p
        assertThat(period.getCostHappyHour()).isEqualByComparingTo("0.20");
    }

    @Test
    void happyHourCostIsZeroWhenThereWasNone() {
        UsageAggregateProjection period = projectionOf(row("NIGHT", "7.5", "10"), row("DAY", "30", "20"));

        assertThat(period.getCostHappyHour()).isEqualByComparingTo("0");
    }

    @Test
    void happyHourKwhIsZeroWhenThereWasNone() {
        UsageAggregateProjection period = projectionOf(row("NIGHT", "7.5", "10"), row("DAY", "30", "20"));

        assertThat(period.getKwhHappyHour()).isEqualByComparingTo("0");
    }

    @Test
    void happyHourUsageIsNeitherOffPeakNorAffectsTheOffPeakFigures() {
        UsageAggregateProjection withHappyHour = projectionOf(
                row("NIGHT", "7.5", "10"),
                row("DAY", "30", "20"),
                row("HAPPY_HOUR", "0", "5"));
        UsageAggregateProjection without = projectionOf(row("NIGHT", "7.5", "10"), row("DAY", "30", "20"));

        assertThat(withHappyHour.getKwhOffPeak()).isEqualByComparingTo("10");
        assertThat(withHappyHour.getCostOffPeak()).isEqualByComparingTo(without.getCostOffPeak());
    }

    @Test
    void singleOrdinaryRateWithAHappyHourIsNotTreatedAsAPeakOffPeakSplit() {
        // The happy-hour rate (0p) is far below half the ordinary 30p, which used to make this
        // look like a split with 0 kWh off-peak. There's no off-peak tariff here at all.
        UsageAggregateProjection period = projectionOf(row("STANDARD", "30", "20"), row("HAPPY_HOUR", "0", "5"));

        assertThat(period.getKwhOffPeak()).isNull();
        assertThat(period.getCostOffPeak()).isNull();
        assertThat(period.getKwhHappyHour()).isEqualByComparingTo("5");
    }

    @Test
    void halfHourInsideAHappyHourStillReportsAnOffPeakFigureOfZeroOnASplitTariff() {
        // by-half-hour pads each interval's breakdown with every other rate seen in the range at
        // 0 kWh (see UsageController.withOtherRatesZeroed), so a happy-hour half-hour on a split
        // tariff carries its own kWh under HAPPY_HOUR and zeroed NIGHT/DAY rows beside it.
        UsageAggregateProjection period = projectionOf(
                row("HAPPY_HOUR", "0", "0.5"),
                row("NIGHT", "7.5", "0"),
                row("DAY", "30", "0"));

        assertThat(period.getKwhOffPeak()).isEqualByComparingTo("0");
        assertThat(period.getKwhHappyHour()).isEqualByComparingTo("0.5");
    }
}
