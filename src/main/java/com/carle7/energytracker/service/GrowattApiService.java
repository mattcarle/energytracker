package com.carle7.energytracker.service;

import com.carle7.energytracker.config.GrowattConfig;
import com.carle7.energytracker.model.GrowattCredentials;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class GrowattApiService {

    private static final Logger logger = LoggerFactory.getLogger(GrowattApiService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    // The Growatt v1 API server-enforces this max range (inclusive) for a single
    // time_unit=day plant/energy call - a wider request fails with error_code 10004. Confirmed
    // live: a 28-day request failed, a 5-day request succeeded.
    private static final int MAX_DAY_QUERY_RANGE = 7;

    @Autowired
    private GrowattConfig growattConfig;

    @Autowired
    private GrowattCredentialsService growattCredentialsService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public PlantListResponse fetchPlantList() {
        String url = growattConfig.getBaseUrl() + "/plant/list";
        return get(url, PlantListResponse.class);
    }

    public PlantDataResponse fetchPlantData(String plantId) {
        String url = String.format("%s/plant/data?plant_id=%s", growattConfig.getBaseUrl(), plantId);
        return get(url, PlantDataResponse.class);
    }

    public PlantPowerResponse fetchPlantPower(String plantId, LocalDate date) {
        String url = String.format("%s/plant/power?plant_id=%s&date=%s",
                growattConfig.getBaseUrl(), plantId, DATE_FORMAT.format(date));
        return get(url, PlantPowerResponse.class);
    }

    // Chunks the requested range into <= MAX_DAY_QUERY_RANGE-day windows (the API enforces this
    // per call, there's no server-side pagination cursor like Octopus's `next` URL - the caller
    // has to slice the range itself), then concatenates the results. Mirrors
    // OctopusApiService.fetchConsumptionData's "keep what succeeded" behaviour: a failed chunk
    // breaks the loop but doesn't discard chunks that already came back.
    public List<EnergyPointDto> fetchDailyEnergy(String plantId, LocalDate fromDate, LocalDate toDate) {
        List<EnergyPointDto> allResults = new ArrayList<>();
        boolean firstChunk = true;

        for (LocalDate[] chunk : chunkDateRange(fromDate, toDate, MAX_DAY_QUERY_RANGE)) {
            String url = String.format("%s/plant/energy?plant_id=%s&start_date=%s&end_date=%s&time_unit=day",
                    growattConfig.getBaseUrl(), plantId, DATE_FORMAT.format(chunk[0]), DATE_FORMAT.format(chunk[1]));
            PlantEnergyResponse response = get(url, PlantEnergyResponse.class);
            if (response == null || response.data == null || response.data.energys == null) {
                if (firstChunk) return null;
                break;
            }
            allResults.addAll(response.data.energys);
            firstChunk = false;
        }

        return allResults;
    }

    List<LocalDate[]> chunkDateRange(LocalDate from, LocalDate to, int maxDaysInclusive) {
        List<LocalDate[]> chunks = new ArrayList<>();
        LocalDate chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = chunkStart.plusDays(maxDaysInclusive - 1);
            if (chunkEnd.isAfter(to)) {
                chunkEnd = to;
            }
            chunks.add(new LocalDate[]{chunkStart, chunkEnd});
            chunkStart = chunkEnd.plusDays(1);
        }
        return chunks;
    }

    private <T extends GrowattEnvelope> T get(String url, Class<T> responseType) {
        GrowattCredentials credentials = growattCredentialsService.getCredentials();

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", credentials.getApiToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class);
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("GET {} completed in {} ms", url, durationMs);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("API error from {}: {} {}", url, response.getStatusCode(), response.getBody());
                return null;
            }

            T parsed;
            try {
                parsed = objectMapper.readValue(response.getBody(), responseType);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse response from {}: {}", url, e.getMessage(), e);
                return null;
            }

            if (parsed.error_code != 0) {
                logger.error("Growatt API error from {}: error_code={} error_msg={}", url, parsed.error_code, parsed.error_msg);
                return null;
            }
            return parsed;
        } catch (Exception e) {
            logger.error("Failed to fetch {}: {}", url, e.getMessage(), e);
            return null;
        }
    }

    // Every Growatt v1 response wraps its payload the same way: {"error_msg":"","data":{...},"error_code":0}.
    // ignoreUnknown=true on every one of these DTOs: Growatt's real responses carry dozens of
    // undocumented fields per object (confirmed live - e.g. plant/list returns country, latitude,
    // image_url, locale, etc. alongside the handful of fields actually used here) that would
    // otherwise fail strict deserialization with the shared, unconfigured ObjectMapper bean.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrowattEnvelope {
        public String error_msg;
        public int error_code;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantListResponse extends GrowattEnvelope {
        public PlantListData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantListData {
        public List<PlantDto> plants;
        public int count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantDto {
        public long plant_id;
        public String name;
        public String create_date;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantDataResponse extends GrowattEnvelope {
        public PlantDataDto data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantDataDto {
        public String total_energy;
        public String today_energy;
        public String monthly_energy;
        public String yearly_energy;
        public double current_power;
        public String last_update_time;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantPowerResponse extends GrowattEnvelope {
        public PlantPowerData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantPowerData {
        public int count;
        public List<PowerPointDto> powers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerPointDto {
        public String time;
        public Double power;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantEnergyResponse extends GrowattEnvelope {
        public PlantEnergyData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlantEnergyData {
        public List<EnergyPointDto> energys;
        public int count;
        public String time_unit;
    }

    // `date` is a JSON string in day/month mode ("2026-08-25", "2026-08") but a bare JSON
    // integer in year mode (2026) - declared as Object so Jackson accepts either without a
    // coercion failure; callers that need the day-mode value read it via String.valueOf(date).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnergyPointDto {
        public Object date;
        public String energy;
    }
}
