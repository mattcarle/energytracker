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

    @Value("${app.startup-usage-load.enabled:true}")
    private boolean startupUsageLoadEnabled;

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
}
