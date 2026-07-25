package com.carle7.energytracker.service;

import com.carle7.energytracker.config.OctopusConfig;
import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.AgreementRepository;
import com.carle7.energytracker.repository.MeterRepository;
import com.carle7.energytracker.repository.UsageRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
    private com.carle7.energytracker.repository.StandingChargeRepository standingChargeRepository;

    @Autowired
    private com.carle7.energytracker.repository.UnitRateRepository unitRateRepository;

    @Autowired
    private com.carle7.energytracker.repository.MeterAgreementRepository meterAgreementRepository;

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
    public void loadAccountDetails() {
        try {
            // Clear existing data before reloading from Octopus API. Order matters because of FK constraints.
            unitRateRepository.deleteAllInBatch();
            standingChargeRepository.deleteAllInBatch();
            meterAgreementRepository.deleteAllInBatch();
            agreementRepository.deleteAllInBatch();
            meterRepository.deleteAllInBatch();

            String jsonResponse = getAccountDetails();
            if (jsonResponse == null) {
                logger.error("Failed to load account details: API returned null response");
                return;
            }

            AccountResponse accountResponse = objectMapper.readValue(jsonResponse, AccountResponse.class);

            if (accountResponse.properties == null) {
                return;
            }

            // Collect all meters from all properties
            List<MeterWithAgreements> metersWithAgreements = new ArrayList<>();
            for (PropertyDto property : accountResponse.properties) {
                collectMetersFromProperty(property, metersWithAgreements);
            }

            // Save all meters
            List<Meter> meters = metersWithAgreements.stream()
                    .map(MeterWithAgreements::meter)
                    .toList();
            long startTime = System.currentTimeMillis();
            List<Meter> savedMeters = meterRepository.saveAll(meters);
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} meter records in {} ms", savedMeters.size(), durationMs);

            // Update metersWithAgreements with saved meter IDs
            for (int i = 0; i < savedMeters.size(); i++) {
                metersWithAgreements.get(i).meter().setId(savedMeters.get(i).getId());
            }

            // Collect unique agreements and save them
            var uniqueAgreements = metersWithAgreements.stream()
                    .flatMap(m -> m.agreementDtos().stream())
                    .distinct()
                    .toList();

            List<Agreement> agreements = uniqueAgreements.stream()
                    .map(dto -> new Agreement(
                            dto.tariff_code,
                            parseDateTime(dto.valid_from),
                            dto.valid_to != null ? parseDateTime(dto.valid_to) : null
                    ))
                    .toList();

            startTime = System.currentTimeMillis();
            List<Agreement> savedAgreements = agreementRepository.saveAll(agreements);
            durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} agreement records in {} ms", savedAgreements.size(), durationMs);

            // Build a map from (tariff_code, valid_from) to saved Agreement
            var agreementMap = new java.util.HashMap<String, Agreement>();
            for (Agreement a : savedAgreements) {
                agreementMap.put(a.getTariffCode() + "|" + a.getValidFrom(), a);
            }

            // Create meter-agreement relationships
            List<com.carle7.energytracker.model.MeterAgreement> meterAgreements = new ArrayList<>();
            for (MeterWithAgreements mwa : metersWithAgreements) {
                for (AgreementDetailDto dto : mwa.agreementDtos()) {
                    String key = dto.tariff_code + "|" + parseDateTime(dto.valid_from);
                    Agreement agreement = agreementMap.get(key);
                    if (agreement != null) {
                        meterAgreements.add(new com.carle7.energytracker.model.MeterAgreement(
                                mwa.meter().getId(),
                                agreement.getId()
                        ));
                    }
                }
            }

            startTime = System.currentTimeMillis();
            meterAgreementRepository.saveAll(meterAgreements);
            durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} meter-agreement records in {} ms", meterAgreements.size(), durationMs);

            // Load standing charges and unit rates for each agreement
            for (Agreement agreement : savedAgreements) {
                String meterType = determineMeterType(agreement, metersWithAgreements);

                var standingChargeResponse = loadStandingCharges(agreement.getTariffCode(), meterType);
                var standingCharges = loadStandingCharges(standingChargeResponse, agreement);
                standingChargeRepository.saveAll(standingCharges);

                var unitRateResponse = loadUnitRates(agreement.getTariffCode(), meterType);
                var unitRates = loadUnitRates(unitRateResponse, agreement);
                unitRateRepository.saveAll(unitRates);
            }
        } catch (Exception e) {
            logger.error("Failed to load account details: {}", e.getMessage(), e);
        }
    }

    private record MeterWithAgreements(Meter meter, List<AgreementDetailDto> agreementDtos, String meterType) {}

    private void collectMetersFromProperty(PropertyDto property, List<MeterWithAgreements> result) {
        if (property.electricity_meter_points != null) {
            for (MeterPointDto meterPoint : property.electricity_meter_points) {
                collectMetersFromMeterPoint(meterPoint, "ELEC", result);
            }
        }
        if (property.gas_meter_points != null) {
            for (MeterPointDto meterPoint : property.gas_meter_points) {
                collectMetersFromMeterPoint(meterPoint, "GAS", result);
            }
        }
    }

    private void collectMetersFromMeterPoint(MeterPointDto meterPoint, String meterType, List<MeterWithAgreements> result) {
        if (meterPoint.meters == null) {
            return;
        }
        for (MeterDetailDto meter : meterPoint.meters) {
            Meter meterRecord = new Meter(
                    "GAS".equals(meterType) ? meterPoint.mprn : meterPoint.mpan,
                    meter.serial_number,
                    false,
                    meterType
            );
            List<AgreementDetailDto> agreementDtos = meterPoint.agreements != null
                    ? new ArrayList<>(meterPoint.agreements)
                    : new ArrayList<>();
            result.add(new MeterWithAgreements(meterRecord, agreementDtos, meterType));
        }
    }

    private String determineMeterType(Agreement agreement, List<MeterWithAgreements> metersWithAgreements) {
        for (MeterWithAgreements mwa : metersWithAgreements) {
            for (AgreementDetailDto dto : mwa.agreementDtos()) {
                if (dto.tariff_code.equals(agreement.getTariffCode())) {
                    return mwa.meterType();
                }
            }
        }
        return "ELEC"; // default
    }

    private List<StandingCharge> loadStandingCharges(StandingChargesResponse sc, Agreement agreementRecord) {
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

    private List<com.carle7.energytracker.model.UnitRate> loadUnitRates(UnitRatesResponse ur, Agreement agreementRecord) {
        List<com.carle7.energytracker.model.UnitRate> unitRates = new ArrayList<>();
        if (ur != null && ur.results != null) {
            for (UnitRateDto urDto : ur.results) {
                LocalDateTime urValidFrom = parseDateTime(urDto.valid_from);
                LocalDateTime urValidTo = urDto.valid_to != null ? parseDateTime(urDto.valid_to) : null;
                BigDecimal valueExc = BigDecimal.valueOf(urDto.value_exc_vat);
                BigDecimal valueInc = BigDecimal.valueOf(urDto.value_inc_vat);
                String paymentMethod = ofNullable(urDto.payment_method).orElse("NA");

                unitRates.add(new com.carle7.energytracker.model.UnitRate(
                        agreementRecord.getId(), valueExc, valueInc, urValidFrom, urValidTo, paymentMethod
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

    private UnitRatesResponse loadUnitRates(String tariffCode, String meterType) {
        String type = switch (meterType) {
            case "GAS" -> "gas";
            case "ELEC" -> "electricity";
            default -> throw new IllegalArgumentException("Invalid meter type: " + meterType);
        };
        String product = computeProductName(tariffCode);
        String url = String.format(
                "%s/products/%s/%s-tariffs/%s/standard-unit-rates/",
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
                return new UnitRatesResponse();
            }

            try {
                return objectMapper.readValue(response.getBody(), UnitRatesResponse.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse unit rates response from {}: {}", url, e.getMessage(), e);
                return new UnitRatesResponse();
            }
        } catch (Exception e) {
            logger.error("Failed to fetch unit rates from {}: {}", url, e.getMessage(), e);
            return new UnitRatesResponse();
        }
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

