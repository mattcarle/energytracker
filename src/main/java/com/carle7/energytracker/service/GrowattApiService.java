package com.carle7.energytracker.service;

import com.carle7.energytracker.config.GrowattConfig;
import com.carle7.energytracker.model.GrowattCredentials;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.annotation.PostConstruct;
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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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

    // Growatt intermittently cuts a response off partway through (seen live on mix_data, the
    // largest payload: the server closes the connection mid-body, surfacing as "chunked transfer
    // encoding ... EOF reached while reading" wrapped in a RestClientException) or drops the
    // connection outright. A retry almost always succeeds, so each request is attempted up to
    // MAX_ATTEMPTS times with a short pause before the 2nd and 3rd. Timeouts are deliberately NOT
    // retried - against a hung server that would multiply the 30 s read timeout.
    private static final int MAX_ATTEMPTS = 3;
    // Not final so a test can zero the pauses.
    private long[] retryBackoffMs = {300, 1000};

    @Autowired
    private GrowattConfig growattConfig;

    @Autowired
    private GrowattCredentialsService growattCredentialsService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // A private copy of the shared ObjectMapper bean (not the bean itself, which
    // OctopusApiService also uses) configured to tolerate one specific Growatt quirk: fields
    // normally typed as a nested object (e.g. mix_data's "data") have been observed coming back
    // as an empty string ("") rather than an object or null when Growatt has nothing to report
    // for the requested window - confirmed live, see the ERROR log this was added to fix
    // ("Cannot coerce empty String ... to MixDataData"). Jackson's default coercion rejects that
    // outright; this copy treats an empty string as null for POJO-typed fields instead of
    // failing the whole parse, without changing how OctopusApiService (or anything else sharing
    // the injected bean) behaves.
    private ObjectMapper growattObjectMapper;

    @PostConstruct
    private void initGrowattObjectMapper() {
        growattObjectMapper = configureGrowattObjectMapper(objectMapper);
    }

    // Package-private (not private) so a test can assert the coercion behaviour directly,
    // without needing a Spring context - same reasoning as UsageRepositoryImpl's package-private
    // SQL templates.
    static ObjectMapper configureGrowattObjectMapper(ObjectMapper base) {
        ObjectMapper mapper = base.copy();
        mapper.coercionConfigFor(LogicalType.POJO)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
        return mapper;
    }

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
    // Returns the error message alongside the points (rather than just null like get()'s other
    // callers get away with) so the Live tab can show the caller *why* nothing came back - see
    // MixDataResult. A later page failing after earlier pages already succeeded still keeps
    // "return what succeeded" behaviour (no error surfaced) - only a page-1 failure, with no
    // data at all to fall back on, propagates its error.
    public MixDataResult fetchMixData(String deviceSn, LocalDate date) {
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

            logger.info("Fetching mix data for page " + page + " params: " + params);

            GrowattResult<MixDataResponse> result = post(url, params, MixDataResponse.class);
            MixDataResponse response = result.body;
            if (response == null || response.data == null || response.data.datas == null) {
                return page == 1 ? new MixDataResult(null, result.error) : new MixDataResult(allResults, null);
            }
            allResults.addAll(response.data.datas);
            if (response.data.datas.size() < MIX_DATA_PER_PAGE || allResults.size() >= response.data.count) {
                break;
            }
        }

        return new MixDataResult(allResults, null);
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
            ResponseEntity<String> response = executeWithRetry(url,
                    () -> restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class));
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("GET {} completed in {} ms", url, durationMs);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("API error from {}: {} {}", url, response.getStatusCode(), response.getBody());
                return null;
            }

            T parsed;
            try {
                parsed = growattObjectMapper.readValue(response.getBody(), responseType);
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
            logRequestFailure("fetch", url, e);
            return null;
        }
    }

    // Device-level endpoints (mix_data, mix_last_data, etc.) are POSTed with form fields rather
    // than GET query params - otherwise identical error/parsing handling to get() above, except
    // this returns the failure reason instead of discarding it: fetchMixData's only caller
    // (GrowattService.getLivePowerCurve, feeding the Live tab) needs to show it, unlike get()'s
    // several callers, which don't - left untouched rather than widening this to every Growatt
    // call this app makes.
    private <T extends GrowattEnvelope> GrowattResult<T> post(String url, MultiValueMap<String, String> formParams, Class<T> responseType) {
        GrowattCredentials credentials = growattCredentialsService.getCredentials();

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", credentials.getApiToken());
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = executeWithRetry(url,
                    () -> restTemplate.postForEntity(URI.create(url), entity, String.class));
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("POST {} completed in {} ms", url, durationMs);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("API error from {}: {} {}", url, response.getStatusCode(), response.getBody());
                return new GrowattResult<>(null, "Growatt API returned " + response.getStatusCode().value());
            }

            T parsed;
            try {
                parsed = growattObjectMapper.readValue(response.getBody(), responseType);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse response from {}: {}", url, e.getMessage(), e);
                return new GrowattResult<>(null, "Failed to parse Growatt API response");
            }

            if (parsed.error_code != 0) {
                logger.error("Growatt API error from {}: error_code={} error_msg={}", url, parsed.error_code, parsed.error_msg);
                String message = parsed.error_msg != null && !parsed.error_msg.isBlank()
                        ? parsed.error_msg
                        : "Growatt API error " + parsed.error_code;
                return new GrowattResult<>(null, message);
            }
            return new GrowattResult<>(parsed, null);
        } catch (Exception e) {
            logRequestFailure("post to", url, e);
            if (isTransientIoFailure(e)) {
                return new GrowattResult<>(null, "Could not get a complete response from Growatt (connection problem, tried "
                        + MAX_ATTEMPTS + " times)");
            }
            return new GrowattResult<>(null, e.getMessage() != null ? e.getMessage() : "Failed to reach Growatt API");
        }
    }

    // Runs one HTTP call, retrying it (see MAX_ATTEMPTS) when it fails with a transient I/O
    // error; anything else - or the last attempt's failure - propagates to the caller as before.
    private ResponseEntity<String> executeWithRetry(String url, Supplier<ResponseEntity<String>> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (attempt >= MAX_ATTEMPTS || !isTransientIoFailure(e)) {
                    throw e;
                }
                logger.warn("Growatt request to {} failed (attempt {}/{}): {} - retrying",
                        url, attempt, MAX_ATTEMPTS, rootCauseSummary(e));
                try {
                    Thread.sleep(retryBackoffMs[Math.min(attempt - 1, retryBackoffMs.length - 1)]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // An I/O failure (a cut-off body, a reset or dropped connection, an unreachable network) that
    // is worth trying again. Timeouts are excluded - see MAX_ATTEMPTS. Package-private so a test
    // can pin down exactly which failures count.
    static boolean isTransientIoFailure(Throwable failure) {
        boolean io = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return false;
            }
            if (cause instanceof IOException) {
                io = true;
            }
        }
        return io;
    }

    private static String rootCauseSummary(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    // A transient I/O failure is logged as just its root cause: the full trace is ~100 lines of
    // servlet-filter frames that add nothing. Anything unexpected keeps its stack trace.
    private void logRequestFailure(String action, String url, Exception e) {
        if (isTransientIoFailure(e)) {
            logger.error("Failed to {} {} after {} attempts: {}", action, url, MAX_ATTEMPTS, rootCauseSummary(e));
        } else {
            logger.error("Failed to {} {}: {}", action, url, e.getMessage(), e);
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
    // only the following are needed here:
    // - `time`, `ppv` (actual PV panel power, zero overnight - unlike plant/power's `pac`,
    //   which mixes in battery activity), `soc` (battery state of charge, 0-100 - confirmed live
    //   tracking the pack's real charge level through a full day), and `plocalLoadTotal` (house
    //   load consumption power, Watts - confirmed live alongside its `elocalLoadToday`/
    //   `elocalLoadTotal` kWh accumulators).
    // - `pacToUserTotal`/`pacToGridTotal` (grid import/export power, Watts - exactly one is
    //   nonzero at a time in practice) and `pcharge1`/`pdischarge1` (battery charge/discharge
    //   power, Watts, same "exactly one nonzero" relationship) - added for the Live tab,
    //   confirmed live via a temporary raw-response dump against the real account (see PR
    //   history, not committed).
    // - `epvtoday` (today's cumulative PV generation, kWh - confirmed live; yes, lowercase
    //   unlike every other *Today field on this DTO family such as epv1Today/epv2Today, not a
    //   typo here).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MixDataPointDto {
        public String time;
        public Double ppv;
        public Integer soc;
        public Double plocalLoadTotal;
        public Double pacToUserTotal;
        public Double pacToGridTotal;
        public Double pcharge1;
        public Double pdischarge1;
        public Double epvtoday;
    }

    // fetchMixData's return type: `points` is null only when there's no data at all (including a
    // fallback to an empty list never happening - see fetchMixData), in which case `error`
    // carries why, straight from the failed call - a real Growatt error_msg when there was one,
    // otherwise a description of the HTTP/parse failure. Both null together means the call
    // legitimately succeeded with nothing to report (not an error - e.g. before the day's first
    // reading), which callers should treat as "try again later", not "show this to the user".
    public static class MixDataResult {
        public final List<MixDataPointDto> points;
        public final String error;

        public MixDataResult(List<MixDataPointDto> points, String error) {
            this.points = points;
            this.error = error;
        }
    }

    // get()/post()'s internal result - not used outside this class (get() itself still returns
    // T directly/null on failure, unchanged; only post() was widened to carry its failure reason,
    // see post()'s own comment for why).
    private static class GrowattResult<T> {
        final T body;
        final String error;

        GrowattResult(T body, String error) {
            this.body = body;
            this.error = error;
        }
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
