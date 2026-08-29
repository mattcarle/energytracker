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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    // mix_data's own page size cap is 100; 5 pages comfortably covers a full day's ~288
    // 5-minute readings with room to spare.
    private static final int MIX_DATA_PER_PAGE = 100;
    private static final int MIX_DATA_MAX_PAGES = 5;

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

    public DeviceListResponse fetchDeviceList(String plantId) {
        String url = String.format("%s/device/list?plant_id=%s", growattConfig.getBaseUrl(), plantId);
        return get(url, DeviceListResponse.class);
    }

    // The plant-level power endpoint (plant/power) reports inverter AC output, not isolated PV -
    // it stays nonzero after dark whenever the battery is discharging, which is what surfaced
    // this in the first place (see the session's investigation: comparing plant/power against
    // this device-level call's own `ppv` field on real data showed plant/power tracking
    // pac/battery activity, while ppv correctly reads exactly 0 overnight). mix_data has no
    // `next`-URL pagination like Octopus, just a page/perpage the caller drives - looped here
    // until a short page or the reported count says there's no more for the day.
    public List<MixDataPointDto> fetchMixData(String deviceSn, LocalDate date) {
        List<MixDataPointDto> allResults = new ArrayList<>();
        String dateStr = DATE_FORMAT.format(date);
        String url = growattConfig.getBaseUrl() + "/device/mix/mix_data";

        for (int page = 1; page <= MIX_DATA_MAX_PAGES; page++) {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("mix_sn", deviceSn);
            params.add("start_date", dateStr);
            params.add("end_date", dateStr);
            params.add("perpage", String.valueOf(MIX_DATA_PER_PAGE));
            params.add("page", String.valueOf(page));

            MixDataResponse response = post(url, params, MixDataResponse.class);
            if (response == null || response.data == null || response.data.datas == null) {
                return page == 1 ? null : allResults;
            }
            allResults.addAll(response.data.datas);
            if (response.data.datas.size() < MIX_DATA_PER_PAGE || allResults.size() >= response.data.count) {
                break;
            }
        }

        return allResults;
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

    // Device-level endpoints (mix_data, mix_last_data, etc.) are POSTed with form fields rather
    // than GET query params - otherwise identical error/parsing handling to get() above.
    private <T extends GrowattEnvelope> T post(String url, MultiValueMap<String, String> formParams, Class<T> responseType) {
        GrowattCredentials credentials = growattCredentialsService.getCredentials();

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", credentials.getApiToken());
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(URI.create(url), entity, String.class);
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("POST {} completed in {} ms", url, durationMs);

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
            logger.error("Failed to post to {}: {}", url, e.getMessage(), e);
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
    public static class DeviceListResponse extends GrowattEnvelope {
        public DeviceListData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceListData {
        public List<DeviceDto> devices;
        public int count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceDto {
        public String device_sn;
        public int type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MixDataResponse extends GrowattEnvelope {
        public MixDataData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MixDataData {
        public List<MixDataPointDto> datas;
        public int count;
    }

    // A real mix_data point carries roughly 150 device/BMS telemetry fields (confirmed live) -
    // only `time` and `ppv` (actual PV panel power, zero overnight - unlike plant/power's `pac`,
    // which mixes in battery activity) are needed here.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MixDataPointDto {
        public String time;
        public Double ppv;
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
