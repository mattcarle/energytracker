package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.UsageByDayGroupByRateAndRateTypeProjection;
import com.carle7.energytracker.repository.UsageByDayProjection;
import com.carle7.energytracker.repository.UsageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Verifies the actual Jackson JSON shape (interface-projection delegation + custom RateBreakdown
// serialize correctly together) rather than just the Java object graph, which Mockito-based unit
// tests can't confirm on their own. Uses its own ObjectMapper (with JavaTimeModule registered)
// rather than the app's - EnergyTrackerApplication's ObjectMapper bean is a plain `new
// ObjectMapper()` with no modules registered at all, a pre-existing characteristic unrelated to
// this change, so relying on it here would fail on LocalDate for reasons outside this test's scope.
class UsageControllerJsonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void usageByDayResponse_serializesBreakdownAsSiblingFieldWithoutDuplicatingMpanOrDate() throws Exception {
        UsageRepository usageRepository = mock(UsageRepository.class);
        UsageController usageController = new UsageController();
        var field = UsageController.class.getDeclaredField("usageRepository");
        field.setAccessible(true);
        field.set(usageController, usageRepository);

        UsageByDayProjection day = mock(UsageByDayProjection.class);
        when(day.getMpan()).thenReturn("2000016292581");
        when(day.getMeterType()).thenReturn("ELEC");
        when(day.getIsExport()).thenReturn(false);
        when(day.getUsageDate()).thenReturn(LocalDate.parse("2026-07-01"));
        when(day.getIntervalCount()).thenReturn(48L);
        when(day.getKwh()).thenReturn(new BigDecimal("15.437"));
        when(day.getCost()).thenReturn(new BigDecimal("0.7879747386"));
        when(day.getAvgRate()).thenReturn(new BigDecimal("0.05104455131178338"));

        UsageByDayGroupByRateAndRateTypeProjection breakdownRow = mock(UsageByDayGroupByRateAndRateTypeProjection.class);
        when(breakdownRow.getUsageDate()).thenReturn(LocalDate.parse("2026-07-01"));
        when(breakdownRow.getRateType()).thenReturn("STANDARD");
        when(breakdownRow.getRate()).thenReturn(new BigDecimal("3.993045"));
        when(breakdownRow.getKwh()).thenReturn(new BigDecimal("14.756"));

        when(usageRepository.findUsageByDay(eq("2000016292581"), any(), any(), anyList())).thenReturn(List.of(day));
        when(usageRepository.findUsageByDayGroupByRateAndRateType(eq("2000016292581"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(breakdownRow));

        UsageController.UsageByDayResponse response = usageController.getUsageByDay(
                "2000016292581", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02"), null);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        JsonNode dayNode = json.get("days").get(0);

        assertThat(dayNode.get("mpan").asText()).isEqualTo("2000016292581");
        assertThat(dayNode.get("usageDate").asText()).isEqualTo("2026-07-01");
        assertThat(dayNode.get("kwh").decimalValue()).isEqualByComparingTo("15.437");
        assertThat(dayNode.has("breakdown")).isTrue();

        JsonNode breakdownNode = dayNode.get("breakdown").get(0);
        assertThat(breakdownNode.get("rateType").asText()).isEqualTo("STANDARD");
        assertThat(breakdownNode.get("rate").decimalValue()).isEqualByComparingTo("3.993045");
        assertThat(breakdownNode.get("kwh").decimalValue()).isEqualByComparingTo("14.756");
        // The user's example explicitly excludes mpan/usageDate from breakdown entries, since
        // they're already present on the parent day.
        assertThat(breakdownNode.has("mpan")).isFalse();
        assertThat(breakdownNode.has("usageDate")).isFalse();
    }
}
