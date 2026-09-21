package com.carle7.energytracker.service;

import com.carle7.energytracker.config.GrowattConfig;
import com.carle7.energytracker.model.GrowattCredentials;
import com.carle7.energytracker.model.SolarGeneration;
import com.carle7.energytracker.repository.SolarDateRangeProjection;
import com.carle7.energytracker.repository.SolarGenerationRepository;
import com.carle7.energytracker.service.GrowattApiService.EnergyPointDto;
import com.carle7.energytracker.service.GrowattApiService.PlantDataDto;
import com.carle7.energytracker.service.GrowattApiService.PlantDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;

@Service
public class GrowattService {

    private static final Logger logger = LoggerFactory.getLogger(GrowattService.class);

    @Autowired
    private GrowattConfig growattConfig;

    @Autowired
    private GrowattApiService growattApiService;

    @Autowired
    private GrowattCredentialsService growattCredentialsService;

    @Autowired
    private SolarGenerationRepository solarGenerationRepository;

    @Transactional
    public PlantLoadResult loadPlant() {
        PlantLoadResult result = new PlantLoadResult();
        try {
            GrowattApiService.PlantListResponse response = growattApiService.fetchPlantList();
            if (response == null || response.data == null || response.data.plants == null || response.data.plants.isEmpty()) {
                result.setError("Growatt API returned no plants for this account");
                return result;
            }

            // Only one plant is supported today - see the plan's scope note on device/plant-level
            // handling.
            PlantDto plant = response.data.plants.get(0);
            String plantId = String.valueOf(plant.plant_id);
            LocalDate installDate = ofNullable(plant.create_date)
                    .map(d -> LocalDate.parse(d, DateTimeFormatter.ISO_LOCAL_DATE))
                    .orElse(null);

            // The device serial number is needed for the live power curve's device-level call
            // (see getLivePowerCurve) - resolved here too so it's ready as soon as the plant
            // itself is. A device-list failure doesn't fail the whole plant load: the daily kWh
            // backfill (which only needs plantId) still works without it, only the live curve
            // would be unavailable until this succeeds on a later call.
            String deviceSn = null;
            GrowattApiService.DeviceListResponse deviceListResponse = growattApiService.fetchDeviceList(plantId);
            if (deviceListResponse != null && deviceListResponse.data != null && deviceListResponse.data.devices != null
                    && !deviceListResponse.data.devices.isEmpty()) {
                deviceSn = deviceListResponse.data.devices.get(0).device_sn;
            } else {
                logger.warn("Could not resolve a device serial number for plant {}; the live power curve will be unavailable", plantId);
            }

            growattCredentialsService.savePlantDetails(plantId, installDate, deviceSn);

            result.setPlantId(plantId);
            result.setPlantName(plant.name);
            result.setInstallDate(installDate);
        } catch (Exception e) {
            logger.error("Failed to load Growatt plant: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        return result;
    }

    @Transactional
    public SolarLoadResult loadSolarData(boolean deleteAll) {
        SolarLoadResult result = new SolarLoadResult();
        try {
            if (deleteAll) {
                solarGenerationRepository.deleteAllInBatch();
            }

            GrowattCredentials credentials = growattCredentialsService.getCredentials();
            String plantId = credentials.getPlantId();
            LocalDate installDate = credentials.getInstallDate();
            if (plantId == null) {
                PlantLoadResult plantLoad = loadPlant();
                if (plantLoad.getError() != null) {
                    result.setError("Could not resolve Growatt plant: " + plantLoad.getError());
                    return result;
                }
                plantId = plantLoad.getPlantId();
                installDate = plantLoad.getInstallDate();
            }
            final String resolvedPlantId = plantId;

            // Per-plant resume point, following the same lesson as OctopusService.loadUsageData:
            // never anchor backfill to one shared/global cutover across independent sources. Only
            // one plant exists today, but this is written as a lookup keyed by plantId so a future
            // second plant would resume independently rather than sharing this one's cutover.
            Map<String, LocalDate> latestByPlant = solarGenerationRepository.findDateRangeByPlantId().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            SolarDateRangeProjection::getPlantId, SolarDateRangeProjection::getLatest));

            LocalDate periodFrom = ofNullable(latestByPlant.get(plantId))
                    .map(latest -> latest.plusDays(1))
                    .orElse(installDate);

            if (periodFrom == null) {
                result.setError("No existing solar data and no plant install date known; run Growatt credential setup again");
                return result;
            }

            // Never fetch "today" - the inverter hasn't finished reporting for it yet, and the
            // 02:00 scheduled run in particular would otherwise request a same-day range that
            // Growatt has no data for at all (it's the middle of the night). Today's total gets
            // picked up once it becomes "yesterday" on the next run.
            LocalDate periodTo = today().minusDays(1);
            if (periodFrom.isAfter(periodTo)) {
                result.setDayCount(0);
                return result;
            }

            List<EnergyPointDto> energyPoints = growattApiService.fetchDailyEnergy(plantId, periodFrom, periodTo);
            if (energyPoints == null) {
                result.setError("Failed to fetch solar generation data from Growatt");
                return result;
            }

            // Look up every existing row for the fetched range in one query, rather than once per
            // day inside the loop below: a find() per iteration forces Hibernate to auto-flush
            // first, and that flush dirty-checks the whole persistence context accumulated so
            // far - O(n) growing on each of up to ~1000 iterations is O(n^2) overall, which was
            // observed hanging for minutes on a real backfill. One bulk fetch + one final
            // saveAll avoids any query-triggered auto-flush inside the loop.
            Map<LocalDate, SolarGeneration> existingByDate = solarGenerationRepository
                    .findByPlantIdAndGenerationDateGreaterThanEqualAndGenerationDateLessThanOrderByGenerationDateAsc(
                            resolvedPlantId, periodFrom, periodTo.plusDays(1))
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(SolarGeneration::getGenerationDate, java.util.function.Function.identity()));

            List<SolarGeneration> toSave = new java.util.ArrayList<>();
            int dayCount = 0;
            for (EnergyPointDto point : energyPoints) {
                LocalDate date = LocalDate.parse(String.valueOf(point.date), DateTimeFormatter.ISO_LOCAL_DATE);
                BigDecimal kwh = new BigDecimal(point.energy);

                SolarGeneration existing = existingByDate.get(date);
                if (existing != null) {
                    existing.setEnergyKwh(kwh);
                    toSave.add(existing);
                } else {
                    toSave.add(new SolarGeneration(resolvedPlantId, date, kwh));
                }
                dayCount++;
            }
            solarGenerationRepository.saveAll(toSave);
            result.setDayCount(dayCount);
        } catch (Exception e) {
            logger.error("Failed to load solar generation data: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        return result;
    }

    // "Today" in Growatt's own time zone (growatt.api.time-zone, default Europe/London), not the
    // JVM default (forced to UTC - see EnergyTrackerApplication). Every "today"/"yesterday" that
    // feeds a Growatt call or a Growatt-dated query must come from here rather than
    // LocalDate.now(), or the date is a day behind between local midnight and 01:00 during BST.
    public LocalDate today() {
        return LocalDate.now(growattConfig.getTimeZone());
    }

    // Device-level (not plant-level) - see fetchMixData's comment for why: the plant-level power
    // endpoint reports inverter AC output rather than isolated PV. Lazily resolves deviceSn via
    // loadPlant() the same way loadSolarData lazily resolves plantId, for credentials saved
    // before this device-level call existed.
    public GrowattApiService.MixDataResult getLivePowerCurve(LocalDate date) {
        GrowattCredentials credentials = growattCredentialsService.getCredentials();
        String deviceSn = credentials.getDeviceSn();
        if (deviceSn == null) {
            PlantLoadResult plantLoad = loadPlant();
            if (plantLoad.getError() != null) {
                logger.error("Could not resolve Growatt device: {}", plantLoad.getError());
                return new GrowattApiService.MixDataResult(null, plantLoad.getError());
            }
            deviceSn = growattCredentialsService.getCredentials().getDeviceSn();
            if (deviceSn == null) {
                return new GrowattApiService.MixDataResult(null, "Could not resolve a Growatt device serial number");
            }
        }

        GrowattApiService.MixDataResult result = growattApiService.fetchMixData(deviceSn, date);
        // Growatt intermittently returns nothing for a single mix_data call - confirmed live,
        // e.g. an empty-string "data" field (see GrowattApiService.configureGrowattObjectMapper)
        // that parses to no points at all. One immediate retry smooths over that transient miss
        // without masking a genuine outage - a real credentials/device problem already returned
        // above, and a second consecutive empty result (whether or not it carries its own error
        // message) is treated as the real answer.
        if (result.points == null || result.points.isEmpty()) {
            result = growattApiService.fetchMixData(deviceSn, date);
        }
        return result;
    }

    public PlantDataDto getLiveStatus() {
        String plantId = growattCredentialsService.getCredentials().getPlantId();
        GrowattApiService.PlantDataResponse response = growattApiService.fetchPlantData(plantId);
        return response != null ? response.data : null;
    }

    // Today's generation so far (kWh, plant-level - the same level the stored daily totals come
    // from), straight from Growatt. Today is never persisted (see loadSolarData: the backfill
    // stops at yesterday and resumes from the latest stored date + 1, so a stored partial row
    // for today would never be corrected), so anything wanting today included in a solar total
    // has to add this on at read time. Null when Growatt can't say - callers treat that as "no
    // figure for today", not as zero.
    public BigDecimal getTodayKwh() {
        PlantDataDto data = getLiveStatus();
        if (data == null || data.today_energy == null || data.today_energy.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(data.today_energy.trim());
        } catch (NumberFormatException e) {
            logger.warn("Growatt returned a non-numeric today_energy value: {}", data.today_energy);
            return null;
        }
    }

    public static class PlantLoadResult {
        private String plantId;
        private String plantName;
        private LocalDate installDate;
        private String error;

        public String getPlantId() {
            return plantId;
        }

        public void setPlantId(String plantId) {
            this.plantId = plantId;
        }

        public String getPlantName() {
            return plantName;
        }

        public void setPlantName(String plantName) {
            this.plantName = plantName;
        }

        public LocalDate getInstallDate() {
            return installDate;
        }

        public void setInstallDate(LocalDate installDate) {
            this.installDate = installDate;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    public static class SolarLoadResult {
        private int dayCount;
        private String error;

        public int getDayCount() {
            return dayCount;
        }

        public void setDayCount(int dayCount) {
            this.dayCount = dayCount;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
