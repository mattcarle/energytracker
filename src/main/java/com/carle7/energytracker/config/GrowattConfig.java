package com.carle7.energytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@PropertySource("classpath:growatt.properties")
@ConfigurationProperties(prefix = "growatt.api")
public class GrowattConfig {
    private String baseUrl;

    // The zone Growatt's own dates/times are in (the plant's local time - "today", mix_data's
    // start_date/end_date and its "yyyy-MM-dd HH:mm:ss" point times). Deliberately separate from
    // the JVM default zone, which EnergyTrackerApplication forces to UTC for persisted
    // timestamps: using LocalDate.now() for a Growatt call would otherwise roll over to the new
    // day an hour late during BST. Octopus code doesn't use this - it converts to/from UTC
    // explicitly (see OctopusService.LONDON_ZONE).
    private ZoneId timeZone = ZoneId.of("Europe/London");

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(ZoneId timeZone) {
        this.timeZone = timeZone;
    }

}
