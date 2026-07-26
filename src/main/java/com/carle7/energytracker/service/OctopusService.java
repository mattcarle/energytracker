package com.carle7.energytracker.service;

import com.carle7.energytracker.config.OctopusConfig;
import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.model.UnitRate;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Service
public class OctopusService {

    private static final Logger logger = LoggerFactory.getLogger(OctopusService.class);

    @Autowired
    private OctopusConfig octopusConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UsageRepository usageRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StandingChargeRepository standingChargeRepository;

    @Autowired
    private UnitRateRepository unitRateRepository;

    @Autowired
    private MeterPointRepository meterPointRepository;

    @Autowired
    private DayAndNightTariffRepository dayAndNightTariffRepository;

    @Autowired
    private UnitRateByHalfHourRepository unitRateByHalfHourRepository;

    private static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");

    public String getConsumption() {
        String url = String.format(
                "%s/electricity-meter-points/%s/meters/%s/consumption/",
                octopusConfig.getBaseUrl(),
                octopusConfig.getMpan(),
                octopusConfig.getMeterSerial()
        );

        String basicAuth = Base64.getEncoder().encodeToString((octopusConfig.getAuthToken() + ":").getBytes());

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Basic " + basicAuth);

        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        try {
            long startTime = System.currentTimeMillis();
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("GET {} completed in {} ms", url, durationMs);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("API error from {}: {} {}", url, response.getStatusCode(), response.getBody());
                return null;
            }

            return response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch consumption from {}: {}", url, e.getMessage(), e);
            return null;
        }
    }

    public String getAccountDetails() {
        String url = String.format(
                "%s/accounts/%s/",
                octopusConfig.getBaseUrl(),
                octopusConfig.getAccountNumber()
        );

        String basicAuth = Base64.getEncoder().encodeToString((octopusConfig.getAuthToken() + ":").getBytes());

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Basic " + basicAuth);

        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        try {
            long startTime = System.currentTimeMillis();
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("GET {} completed in {} ms", url, durationMs);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("API error from {}: {} {}", url, response.getStatusCode(), response.getBody());
                return null;
            }

            return response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch account details from {}: {}", url, e.getMessage(), e);
            return null;
        }
    }

    @Transactional
    public AccountLoadResult loadAccountDetails() {
        AccountLoadResult result = new AccountLoadResult();
        try {
            // Clear existing data before reloading from Octopus API. Order matters because of FK constraints.
            unitRateByHalfHourRepository.deleteAllInBatch();
            unitRateRepository.deleteAllInBatch();
            standingChargeRepository.deleteAllInBatch();
            agreementRepository.deleteAllInBatch();
            meterRepository.deleteAllInBatch();
            meterPointRepository.deleteAllInBatch();

            String jsonResponse = getAccountDetails();
            if (jsonResponse == null) {
                logger.error("Failed to load account details: API returned null response");
                result.setError("API returned null response");
                return result;
            }

            AccountResponse accountResponse = objectMapper.readValue(jsonResponse, AccountResponse.class);

            if (accountResponse.properties == null) {
                return result;
            }

            // Collect all meter points from all properties
            List<MeterPointData> meterPointsData = new ArrayList<>();
            for (PropertyDto property : accountResponse.properties) {
                collectMeterPointsFromProperty(property, meterPointsData);
            }

            // Save all meter points
            List<MeterPoint> meterPoints = meterPointsData.stream()
                    .map(MeterPointData::meterPoint)
                    .toList();
            long startTime = System.currentTimeMillis();
            List<MeterPoint> savedMeterPoints = meterPointRepository.saveAll(meterPoints);
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} meter point records in {} ms", savedMeterPoints.size(), durationMs);
            result.setMeterPointCount(savedMeterPoints.size());

            // Update meterPointsData with saved meter point IDs
            for (int i = 0; i < savedMeterPoints.size(); i++) {
                meterPointsData.get(i).meterPoint().setId(savedMeterPoints.get(i).getId());
            }

            // Save meters, one per physical meter on each meter point
            List<Meter> meters = new ArrayList<>();
            for (MeterPointData mpd : meterPointsData) {
                for (MeterDetailDto meterDto : mpd.meterDtos()) {
                    meters.add(new Meter(meterDto.serial_number, mpd.meterPoint().getId()));
                }
            }
            startTime = System.currentTimeMillis();
            List<Meter> savedMeters = meterRepository.saveAll(meters);
            durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} meter records in {} ms", savedMeters.size(), durationMs);
            result.setMeterCount(savedMeters.size());

            // Collect agreements per meter point, deduplicating by tariff_code/valid_from within each meter point
            List<Agreement> agreements = new ArrayList<>();
            for (MeterPointData mpd : meterPointsData) {
                var uniqueAgreementDtos = new java.util.LinkedHashMap<String, AgreementDetailDto>();
                for (AgreementDetailDto dto : mpd.agreementDtos()) {
                    uniqueAgreementDtos.putIfAbsent(dto.tariff_code + "|" + dto.valid_from, dto);
                }
                for (AgreementDetailDto dto : uniqueAgreementDtos.values()) {
                    agreements.add(new Agreement(
                            dto.tariff_code,
                            parseDateTime(dto.valid_from),
                            dto.valid_to != null ? parseDateTime(dto.valid_to) : null,
                            mpd.meterPoint().getId()
                    ));
                }
            }

            startTime = System.currentTimeMillis();
            List<Agreement> savedAgreements = agreementRepository.saveAll(agreements);
            durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} agreement records in {} ms", savedAgreements.size(), durationMs);
            result.setAgreementCount(savedAgreements.size());

            // Build a map from meter point ID to meter type for standing charge / unit rate lookups
            var meterTypeByMeterPointId = new java.util.HashMap<Long, String>();
            for (MeterPointData mpd : meterPointsData) {
                meterTypeByMeterPointId.put(mpd.meterPoint().getId(), mpd.meterPoint().getMeterType());
            }

            // Load standing charges and unit rates for each agreement
            int standingChargeCount = 0;
            int unitRateCount = 0;
            for (Agreement agreement : savedAgreements) {
                String meterType = meterTypeByMeterPointId.getOrDefault(agreement.getMeterPointId(), "ELEC");

                var standingChargeResponse = loadStandingCharges(agreement.getTariffCode(), meterType);
                var standingCharges = mapStandingChargesResponse(standingChargeResponse, agreement);
                standingChargeRepository.saveAll(standingCharges);
                standingChargeCount += standingCharges.size();

                // Check if this is a day-and-night tariff
                boolean isDayAndNightTariff = dayAndNightTariffRepository
                        .findByTariffCode(agreement.getTariffCode())
                        .isPresent();

                if (isDayAndNightTariff) {
                    // Load day and night rates from separate endpoints
                    var dayRates = fetchAllUnitRates(agreement, meterType, "day", "DAY");
                    unitRateRepository.saveAll(dayRates);
                    unitRateCount += dayRates.size();

                    var nightRates = fetchAllUnitRates(agreement, meterType, "night", "NIGHT");
                    unitRateRepository.saveAll(nightRates);
                    unitRateCount += nightRates.size();
                } else {
                    // Load standard rates
                    var unitRates = fetchAllUnitRates(agreement, meterType, "standard", "STANDARD");
                    unitRateRepository.saveAll(unitRates);
                    unitRateCount += unitRates.size();
                }
            }
            result.setStandingChargeCount(standingChargeCount);
            result.setUnitRateCount(unitRateCount);
            result.setUnitRatesByHalfHourCount(this.populateHalfHourlyUnitRates());
        } catch (Exception e) {
            logger.error("Failed to load account details: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        return result;
    }

    public static class AccountLoadResult {
        private int meterPointCount;
        private int meterCount;
        private int agreementCount;
        private int standingChargeCount;
        private int unitRateCount;
        private int unitRatesByHalfHourCount;
        private String error;

        public int getMeterPointCount() {
            return meterPointCount;
        }

        public void setMeterPointCount(int meterPointCount) {
            this.meterPointCount = meterPointCount;
        }

        public int getMeterCount() {
            return meterCount;
        }

        public void setMeterCount(int meterCount) {
            this.meterCount = meterCount;
        }

        public int getAgreementCount() {
            return agreementCount;
        }

        public void setAgreementCount(int agreementCount) {
            this.agreementCount = agreementCount;
        }

        public int getStandingChargeCount() {
            return standingChargeCount;
        }

        public void setStandingChargeCount(int standingChargeCount) {
            this.standingChargeCount = standingChargeCount;
        }

        public int getUnitRateCount() {
            return unitRateCount;
        }

        public void setUnitRateCount(int unitRateCount) {
            this.unitRateCount = unitRateCount;
        }

        public int getUnitRatesByHalfHourCount() {
            return unitRatesByHalfHourCount;
        }

        public void setUnitRatesByHalfHourCount(int unitRatesByHalfHourCount) {
            this.unitRatesByHalfHourCount = unitRatesByHalfHourCount;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    private record MeterPointData(MeterPoint meterPoint, List<MeterDetailDto> meterDtos, List<AgreementDetailDto> agreementDtos) {}

    private void collectMeterPointsFromProperty(PropertyDto property, List<MeterPointData> result) {
        if (property.electricity_meter_points != null) {
            for (MeterPointDto meterPointDto : property.electricity_meter_points) {
                collectMeterPointData(meterPointDto, "ELEC", result);
            }
        }
        if (property.gas_meter_points != null) {
            for (MeterPointDto meterPointDto : property.gas_meter_points) {
                collectMeterPointData(meterPointDto, "GAS", result);
            }
        }
    }

    private void collectMeterPointData(MeterPointDto meterPointDto, String meterType, List<MeterPointData> result) {
        MeterPoint meterPoint = new MeterPoint(
                "GAS".equals(meterType) ? meterPointDto.mprn : meterPointDto.mpan,
                ofNullable(meterPointDto.is_export).orElse(false),
                meterType
        );
        List<MeterDetailDto> meterDtos = meterPointDto.meters != null
                ? new ArrayList<>(meterPointDto.meters)
                : new ArrayList<>();
        List<AgreementDetailDto> agreementDtos = meterPointDto.agreements != null
                ? new ArrayList<>(meterPointDto.agreements)
                : new ArrayList<>();
        result.add(new MeterPointData(meterPoint, meterDtos, agreementDtos));
    }

    private List<StandingCharge> mapStandingChargesResponse(StandingChargesResponse sc, Agreement agreementRecord) {
        List<StandingCharge> standingCharges = new ArrayList<>();
        if (sc != null && sc.results != null) {
            for (StandingChargeDto scDto : sc.results) {
                LocalDateTime scValidFrom = parseDateTime(scDto.valid_from);
                LocalDateTime scValidTo = scDto.valid_to != null ? parseDateTime(scDto.valid_to) : null;
                BigDecimal valueExc = BigDecimal.valueOf(scDto.value_exc_vat);
                BigDecimal valueInc = BigDecimal.valueOf(scDto.value_inc_vat);
                String paymentMethod = ofNullable(scDto.payment_method).orElse("NA");

                standingCharges.add(new StandingCharge(
                        agreementRecord.getId(), valueExc, valueInc, scValidFrom, scValidTo, paymentMethod
                ));
            }
        }
        return standingCharges;
    }

    private List<UnitRate> mapUnitRatesResponse(UnitRatesResponse ur, Agreement agreementRecord, String rateType) {
        List<UnitRate> unitRates = new ArrayList<>();
        if (ur != null && ur.results != null) {
            for (UnitRateDto urDto : ur.results) {
                LocalDateTime urValidFrom = parseDateTime(urDto.valid_from);
                LocalDateTime urValidTo = urDto.valid_to != null ? parseDateTime(urDto.valid_to) : null;
                BigDecimal valueExc = BigDecimal.valueOf(urDto.value_exc_vat);
                BigDecimal valueInc = BigDecimal.valueOf(urDto.value_inc_vat);
                String paymentMethod = ofNullable(urDto.payment_method).orElse("NA");

                unitRates.add(new UnitRate(
                        agreementRecord.getId(), valueExc, valueInc, urValidFrom, urValidTo, paymentMethod, rateType
                ));
            }
        }
        return unitRates;
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        return OffsetDateTime.parse(dateTimeString).toLocalDateTime();
    }

    /**
     * Compute product name from tariff code by removing the first two tokens and the last token.
     * Example: E-1R-INTELLI-FIX-12M-26-02-11-H -> INTELLI-FIX-12M-26-02-11
     */
    private String computeProductName(String tariffCode) {
        if (tariffCode == null) return "";
        String[] parts = tariffCode.split("-");
        if (parts.length <= 3) {
            return tariffCode;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < parts.length - 1; i++) {
            if (!sb.isEmpty()) sb.append('-');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private StandingChargesResponse loadStandingCharges(String tariffCode, String meterType) {
        String type = switch (meterType) {
            case "GAS" -> "gas";
            case "ELEC" -> "electricity";
            default -> throw new IllegalArgumentException("Invalid meter type: " + meterType);
        };
        String product = computeProductName(tariffCode);
        String url = String.format(
                "%s/products/%s/%s-tariffs/%s/standing-charges/",
                octopusConfig.getBaseUrl(),
                product,
                type,
                tariffCode
        );

        String basicAuth = Base64.getEncoder().encodeToString((octopusConfig.getAuthToken() + ":").getBytes());
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Basic " + basicAuth);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        try {
            long startTime = System.currentTimeMillis();
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("GET {} completed in {} ms", url, durationMs);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("API error from {}: {} {}", url, response.getStatusCode(), response.getBody());
                return new StandingChargesResponse();
            }

            try {
                return objectMapper.readValue(response.getBody(), StandingChargesResponse.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse standing charges response from {}: {}", url, e.getMessage(), e);
                return new StandingChargesResponse();
            }
        } catch (Exception e) {
            logger.error("Failed to fetch standing charges from {}: {}", url, e.getMessage(), e);
            return new StandingChargesResponse();
        }
    }

    private List<UnitRate> fetchAllUnitRates(Agreement agreement, String meterType, String rateType, String rateTypeLabel) {
        String type = switch (meterType) {
            case "GAS" -> "gas";
            case "ELEC" -> "electricity";
            default -> throw new IllegalArgumentException("Invalid meter type: " + meterType);
        };
        String product = computeProductName(agreement.getTariffCode());
        String endpoint = switch (rateType) {
            case "day" -> "day-unit-rates";
            case "night" -> "night-unit-rates";
            default -> "standard-unit-rates";
        };

        // Build period parameters
        String periodFrom = agreement.getValidFrom().atOffset(java.time.ZoneOffset.UTC).toString();

        // Omit period_to if it equals period_from or is null
        boolean includePeriodTo = agreement.getValidTo() != null
                && !agreement.getValidTo().equals(agreement.getValidFrom());

        String periodTo = ofNullable(agreement.getValidTo()).map(v -> v.atOffset(java.time.ZoneOffset.UTC).toString()).orElse(null);
        String initialUrl;
        if (includePeriodTo) {
            initialUrl = String.format(
                    "%s/products/%s/%s-tariffs/%s/%s/?period_from=%s&period_to=%s&page_size=1500",
                    octopusConfig.getBaseUrl(),
                    product,
                    type,
                    agreement.getTariffCode(),
                    endpoint,
                    periodFrom,
                    periodTo
            );
        } else {
            initialUrl = String.format(
                    "%s/products/%s/%s-tariffs/%s/%s/?period_from=%s&page_size=1500",
                    octopusConfig.getBaseUrl(),
                    product,
                    type,
                    agreement.getTariffCode(),
                    endpoint,
                    periodFrom
            );
        }

        List<UnitRate> allUnitRates = new ArrayList<>();
        String nextUrl = initialUrl;

        String basicAuth = Base64.getEncoder().encodeToString((octopusConfig.getAuthToken() + ":").getBytes());
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Basic " + basicAuth);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        while (nextUrl != null) {
            try {
                long startTime = System.currentTimeMillis();
                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        nextUrl,
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        String.class
                );
                long durationMs = System.currentTimeMillis() - startTime;
                logger.info("GET {} completed in {} ms", nextUrl, durationMs);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    logger.error("API error from {}: {} {}", nextUrl, response.getStatusCode(), response.getBody());
                    break;
                }

                UnitRatesResponse unitRatesResponse = objectMapper.readValue(response.getBody(), UnitRatesResponse.class);
                var unitRates = mapUnitRatesResponse(unitRatesResponse, agreement, rateTypeLabel);
                allUnitRates.addAll(unitRates);

                nextUrl = unitRatesResponse.next;
            } catch (Exception e) {
                logger.error("Failed to fetch unit rates from {}: {}", nextUrl, e.getMessage(), e);
                break;
            }
        }

        logger.info("Fetched {} {} unit rates for tariff {} between {} and {}", allUnitRates.size(), rateTypeLabel, agreement.getTariffCode(), periodFrom, periodTo);
        return allUnitRates;
    }

    public void refreshData() {
        try {
            String jsonResponse = getConsumption();
            if (jsonResponse == null) {
                logger.warn("No consumption data available; skipping refresh");
                return;
            }
            ConsumptionResponse response = objectMapper.readValue(jsonResponse, ConsumptionResponse.class);

            if (response.results != null) {
                for (ConsumptionDto data : response.results) {
                    Usage usage = new Usage(
                            data.getIntervalStartAsLocalDateTime(),
                            data.getIntervalEndAsLocalDateTime(),
                            new BigDecimal(data.consumption)
                    );
                    usageRepository.save(usage);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to refresh consumption data: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public int populateHalfHourlyUnitRates() {
        List<Agreement> agreements = agreementRepository.findAll();
        // Keyed by the same tuple as UNIT_RATE_BY_HALF_HOUR's unique constraint, so overlapping
        // source data (e.g. superseded unit_rate periods) can never produce a duplicate insert.
        Map<String, UnitRateByHalfHour> slotsByKey = new LinkedHashMap<>();
        int duplicatesSkipped = 0;

        for (Agreement agreement : agreements) {
            LocalDateTime windowEnd = agreement.getValidTo() != null
                    ? agreement.getValidTo()
                    : LocalDateTime.now().plusDays(90);

            List<UnitRate> rates = unitRateRepository.findByAgreementIdOrderByValidFrom(agreement.getId()).stream()
                    .filter(r -> !r.getValidFrom().isBefore(agreement.getValidFrom()) && r.getValidFrom().isBefore(windowEnd))
                    .toList();

            Map<String, List<UnitRate>> series = groupIntoSeries(rates);

            Optional<DayAndNightTariff> dnt = dayAndNightTariffRepository.findByTariffCode(agreement.getTariffCode());

            List<UnitRateByHalfHour> agreementSlots = new ArrayList<>();
            if (dnt.isEmpty()) {
                for (List<UnitRate> s : series.values()) {
                    agreementSlots.addAll(expandSeriesToHalfHourSlots(s, windowEnd));
                }
            } else {
                agreementSlots.addAll(expandDayAndNightSeries(agreement, series, dnt.get(), windowEnd));
            }

            for (UnitRateByHalfHour slot : agreementSlots) {
                String key = slot.getAgreementId() + "|" + slot.getValidFrom() + "|" + slot.getPaymentMethod() + "|" + slot.getRateType();
                if (slotsByKey.putIfAbsent(key, slot) != null) {
                    duplicatesSkipped++;
                }
            }
        }

        if (duplicatesSkipped > 0) {
            logger.warn("Skipped {} duplicate half-hourly slots (overlapping unit_rate periods)", duplicatesSkipped);
        }

        long startTime = System.currentTimeMillis();
        List<UnitRateByHalfHour> saved = unitRateByHalfHourRepository.saveAll(new ArrayList<>(slotsByKey.values()));
        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Saved {} half-hourly unit rate records in {} ms", saved.size(), durationMs);

        return saved.size();
    }

    private Map<String, List<UnitRate>> groupIntoSeries(List<UnitRate> rates) {
        Map<String, List<UnitRate>> series = new LinkedHashMap<>();
        for (UnitRate rate : rates) {
            String key = rate.getRateType() + "|" + rate.getPaymentMethod();
            series.computeIfAbsent(key, k -> new ArrayList<>()).add(rate);
        }
        return series;
    }

    private List<UnitRateByHalfHour> expandSeriesToHalfHourSlots(List<UnitRate> series, LocalDateTime windowEnd) {
        List<UnitRateByHalfHour> slots = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            UnitRate rate = series.get(i);
            LocalDateTime rawEndBound = i + 1 < series.size()
                    ? series.get(i + 1).getValidFrom()
                    : (rate.getValidTo() != null ? rate.getValidTo() : windowEnd);
            // Never generate slots past the agreement's own window, even if the unit_rate
            // record's own valid_to extends further (e.g. a superseded price-change record).
            LocalDateTime endBound = rawEndBound.isBefore(windowEnd) ? rawEndBound : windowEnd;

            LocalDateTime slot = rate.getValidFrom();
            while (slot.isBefore(endBound)) {
                slots.add(new UnitRateByHalfHour(
                        rate.getAgreementId(),
                        rate.getValueExcVat(),
                        rate.getValueIncVat(),
                        slot,
                        slot.plusMinutes(30),
                        rate.getPaymentMethod(),
                        rate.getRateType()
                ));
                slot = slot.plusMinutes(30);
            }
        }
        return slots;
    }

    private List<UnitRateByHalfHour> expandDayAndNightSeries(Agreement agreement, Map<String, List<UnitRate>> series, DayAndNightTariff dnt, LocalDateTime windowEnd) {
        Map<LocalDateTime, UnitRateByHalfHour> daySlots = new java.util.HashMap<>();
        Map<LocalDateTime, UnitRateByHalfHour> nightSlots = new java.util.HashMap<>();

        for (Map.Entry<String, List<UnitRate>> entry : series.entrySet()) {
            String rateType = entry.getKey().split("\\|", 2)[0];
            for (UnitRateByHalfHour halfHour : expandSeriesToHalfHourSlots(entry.getValue(), windowEnd)) {
                if ("DAY".equals(rateType)) {
                    daySlots.put(halfHour.getValidFrom(), halfHour);
                } else if ("NIGHT".equals(rateType)) {
                    nightSlots.put(halfHour.getValidFrom(), halfHour);
                }
            }
        }

        List<UnitRateByHalfHour> slots = new ArrayList<>();
        LocalDateTime slot = agreement.getValidFrom();
        while (slot.isBefore(windowEnd)) {
            LocalTime localTime = slot.atZone(ZoneOffset.UTC).withZoneSameInstant(LONDON_ZONE).toLocalTime();
            boolean isNight = isNightTime(localTime, dnt.getNightRateValidFrom(), dnt.getDayRateValidFrom());
            UnitRateByHalfHour source = isNight ? nightSlots.get(slot) : daySlots.get(slot);
            if (source != null) {
                slots.add(new UnitRateByHalfHour(
                        source.getAgreementId(),
                        source.getValueExcVat(),
                        source.getValueIncVat(),
                        slot,
                        slot.plusMinutes(30),
                        source.getPaymentMethod(),
                        isNight ? "NIGHT" : "DAY"
                ));
            }
            slot = slot.plusMinutes(30);
        }
        return slots;
    }

    private boolean isNightTime(LocalTime time, LocalTime nightFrom, LocalTime dayFrom) {
        if (nightFrom.isAfter(dayFrom)) {
            return !time.isBefore(nightFrom) || time.isBefore(dayFrom);
        }
        return !time.isBefore(nightFrom) && time.isBefore(dayFrom);
    }

    public static class AccountResponse {
        public String number;
        public List<PropertyDto> properties;
    }

    public static class PropertyDto {
        public int id;
        public String moved_in_at;
        public String moved_out_at;
        public String address_line_1;
        public String address_line_2;
        public String address_line_3;
        public String town;
        public String county;
        public String postcode;
        public List<MeterPointDto> electricity_meter_points;
        public List<MeterPointDto> gas_meter_points;
    }

    public static class MeterPointDto {
        public String mpan;
        public String mprn;
        public int profile_class;
        public int consumption_standard;
        public List<MeterDetailDto> meters;
        public List<AgreementDetailDto> agreements;
        public Boolean is_export;
    }

    public static class MeterDetailDto {
        public String serial_number;
        public List<RegisterDto> registers;
    }

    public static class RegisterDto {
        public String identifier;
        public String rate;
        public boolean is_settlement_register;
    }

    public static class AgreementDetailDto {
        public String tariff_code;
        public String valid_from;
        public String valid_to;
    }

    public static class ConsumptionResponse {
        public int count;
        public String next;
        public String previous;
        public List<ConsumptionDto> results;
    }

    public static class ConsumptionDto {
        public double consumption;
        public String interval_start;
        public String interval_end;

        public LocalDateTime getIntervalStartAsLocalDateTime() {
            return OffsetDateTime.parse(interval_start).toLocalDateTime();
        }

        public LocalDateTime getIntervalEndAsLocalDateTime() {
            return OffsetDateTime.parse(interval_end).toLocalDateTime();
        }
    }

    public static class StandingChargesResponse {
        public int count;
        public String next;
        public String previous;
        public List<StandingChargeDto> results;
    }

    public static class StandingChargeDto {
        public double value_exc_vat;
        public double value_inc_vat;
        public String valid_from;
        public String valid_to;
        public String payment_method;
    }

    public static class UnitRatesResponse {
        public int count;
        public String next;
        public String previous;
        public List<UnitRateDto> results;
    }

    public static class UnitRateDto {
        public double value_exc_vat;
        public double value_inc_vat;
        public String valid_from;
        public String valid_to;
        public String payment_method;
    }
}

