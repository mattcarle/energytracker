package com.carle7.energytracker.service;

import com.carle7.energytracker.config.GrowattConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// GrowattService.today() must follow growatt.api.time-zone, not the JVM default zone (which the
// app forces to UTC - see EnergyTrackerApplication): between local midnight and 01:00 during BST
// the two are on different dates, which is what made the Live tab request yesterday's mix_data.
class GrowattServiceTodayTest {

    @Test
    void defaultsToLondon() {
        assertThat(new GrowattConfig().getTimeZone()).isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    void timeZonePropertyBindsToZoneId() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(
                Map.of("growatt.api.time-zone", "Pacific/Auckland"));

        GrowattConfig config = new Binder(source).bind("growatt.api", GrowattConfig.class).get();

        assertThat(config.getTimeZone()).isEqualTo(ZoneId.of("Pacific/Auckland"));
    }

    @Test
    void todayUsesConfiguredZoneNotJvmDefault() {
        // UTC+14 and UTC-11: at any instant at least one of them is on a different date from UTC,
        // so a today() that fell back to the JVM default zone would fail for that one.
        boolean sawDateDifferentFromUtc = false;
        for (String zone : new String[]{"Pacific/Kiritimati", "Pacific/Pago_Pago"}) {
            GrowattConfig config = new GrowattConfig();
            config.setTimeZone(ZoneId.of(zone));
            GrowattService service = new GrowattService();
            ReflectionTestUtils.setField(service, "growattConfig", config);

            // Re-read the expected value after the call so a midnight rollover mid-test can't flake it.
            LocalDate before = LocalDate.now(ZoneId.of(zone));
            LocalDate actual = service.today();
            LocalDate after = LocalDate.now(ZoneId.of(zone));

            assertThat(actual).isBetween(before, after);
            sawDateDifferentFromUtc |= !actual.equals(LocalDate.now(ZoneId.of("UTC")));
        }
        assertThat(sawDateDifferentFromUtc).isTrue();
    }
}
