package com.carle7.energytracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataLoadScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DataLoadScheduler.class);

    @Autowired
    private OctopusService octopusService;

    @Autowired
    private OctopusCredentialsService octopusCredentialsService;

    @Autowired
    private GrowattService growattService;

    @Autowired
    private GrowattCredentialsService growattCredentialsService;

    @Value("${app.startup-usage-load.enabled:true}")
    private boolean startupUsageLoadEnabled;

    @Value("${app.startup-solar-load.enabled:true}")
    private boolean startupSolarLoadEnabled;

    // First-run setup already performs the initial account+usage load itself (see
    // AuthController#setup), so this is only reached on subsequent restarts, where it catches up
    // on whatever usage has landed since the app last ran. Dev disables this (see
    // application-dev.properties) so restarts aren't held up by an API round-trip.
    @EventListener(ApplicationReadyEvent.class)
    public void loadLatestUsageOnStartup() {
        if (!startupUsageLoadEnabled) {
            logger.info("Skipping startup usage load (app.startup-usage-load.enabled=false)");
            return;
        }
        if (!octopusCredentialsService.hasCredentials()) {
            return;
        }
        logger.info("Loading latest usage data on startup");
        OctopusService.UsageLoadResult result = octopusService.loadUsageData(false);
        if (result.getError() != null) {
            logger.error("Startup usage load failed: {}", result.getError());
        } else {
            logger.info("Startup usage load complete: {} usage record(s) loaded", result.getUsageCount());
        }
    }

    // Octopus publishes each day's readings, and any tariff/agreement changes, overnight - 02:00
    // UK time gives them time to land before we pull the latest agreements and usage.
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/London")
    public void loadLatestDataDaily() {
        if (!octopusCredentialsService.hasCredentials()) {
            return;
        }
        logger.info("Running scheduled daily data load");

        OctopusService.AccountLoadResult accountResult = octopusService.loadAccountData(false);
        if (accountResult.getError() != null) {
            logger.error("Scheduled agreement load failed: {}", accountResult.getError());
        }

        OctopusService.UsageLoadResult usageResult = octopusService.loadUsageData(false);
        if (usageResult.getError() != null) {
            logger.error("Scheduled usage load failed: {}", usageResult.getError());
        }

        logger.info("Scheduled daily data load complete");
    }

    // Same startup-catchup role as loadLatestUsageOnStartup, but gated by Growatt's own
    // (independent) credentials state - kept separate rather than folded into that method so one
    // integration's missing credentials never block the other's catch-up load.
    @EventListener(ApplicationReadyEvent.class)
    public void loadLatestSolarDataOnStartup() {
        if (!startupSolarLoadEnabled) {
            logger.info("Skipping startup solar load (app.startup-solar-load.enabled=false)");
            return;
        }
        if (!growattCredentialsService.hasCredentials()) {
            return;
        }
        logger.info("Loading latest solar generation data on startup");
        GrowattService.SolarLoadResult result = growattService.loadSolarData(false);
        if (result.getError() != null) {
            logger.error("Startup solar load failed: {}", result.getError());
        } else {
            logger.info("Startup solar load complete: {} day(s) loaded", result.getDayCount());
        }
    }

    // Solar totals for "today" only firm up once the inverter has finished reporting for the
    // day - runs alongside the existing 02:00 Octopus job so one nightly window covers both,
    // but kept as its own method (not merged into loadLatestDataDaily) since it has an
    // independent credentials gate that shouldn't couple to Octopus's.
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/London")
    public void loadLatestSolarDataDaily() {
        if (!growattCredentialsService.hasCredentials()) {
            return;
        }
        GrowattService.SolarLoadResult result = growattService.loadSolarData(false);
        if (result.getError() != null) {
            logger.error("Scheduled solar load failed: {}", result.getError());
        }
    }
}
