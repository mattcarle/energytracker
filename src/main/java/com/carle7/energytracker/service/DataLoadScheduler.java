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

    // Octopus's usage readings land with a lag of a day or more, at no fixed time, so they're
    // checked for hourly rather than once a night. Cheap to repeat: each run re-fetches from the
    // start of the previous day per meter (a call or two each) and does nothing when there's
    // nothing new. Runs at 15 past so it never coincides with the 02:00 jobs below, which would
    // otherwise all start together.
    @Scheduled(cron = "0 15 * * * *", zone = "Europe/London")
    public void loadLatestUsageHourly() {
        if (!octopusCredentialsService.hasCredentials()) {
            return;
        }
        logger.info("Running scheduled hourly usage load");
        OctopusService.UsageLoadResult usageResult = octopusService.loadUsageData(false);
        if (usageResult.getError() != null) {
            logger.error("Scheduled usage load failed: {}", usageResult.getError());
        } else {
            logger.info("Scheduled hourly usage load complete: {} usage record(s) loaded", usageResult.getUsageCount());
        }
    }

    // Agreements, standing charges and unit rates change rarely and are published overnight - 02:00
    // UK time gives any tariff change time to land. Deliberately not run hourly with the usage
    // above: it re-pulls the account and around 90 days of unit rates each time, far more API
    // traffic (and database churn) than a usage refresh, for data that only changes once a day.
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/London")
    public void loadLatestAccountDataDaily() {
        if (!octopusCredentialsService.hasCredentials()) {
            return;
        }
        logger.info("Running scheduled daily account data load");
        OctopusService.AccountLoadResult accountResult = octopusService.loadAccountData(false);
        if (accountResult.getError() != null) {
            logger.error("Scheduled agreement load failed: {}", accountResult.getError());
        }
        logger.info("Scheduled daily account data load complete");
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
    // day - stays once a day, at 02:00 alongside the Octopus account-data job so one nightly
    // window covers both, but kept as its own method (not merged into
    // loadLatestAccountDataDaily) since it has an independent credentials gate that shouldn't
    // couple to Octopus's.
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
