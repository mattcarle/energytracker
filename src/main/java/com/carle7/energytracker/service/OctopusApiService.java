package com.carle7.energytracker.service;

import com.carle7.energytracker.config.OctopusConfig;
import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.model.UnitRate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static java.util.Optional.ofNullable;

@Service
public class OctopusApiService {

    private static final Logger logger = LoggerFactory.getLogger(OctopusApiService.class);

    @Autowired
    private OctopusConfig octopusConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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

    public String fetchAccountDetails() {
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

    public List<StandingCharge> fetchStandingCharges(Agreement agreement, String meterType) {
        String tariffCode = agreement.getTariffCode();
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
                return mapStandingChargesResponse(new StandingChargesResponse(), agreement);
            }

            try {
                StandingChargesResponse scResponse = objectMapper.readValue(response.getBody(), StandingChargesResponse.class);
                return mapStandingChargesResponse(scResponse, agreement);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse standing charges response from {}: {}", url, e.getMessage(), e);
                return mapStandingChargesResponse(new StandingChargesResponse(), agreement);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch standing charges from {}: {}", url, e.getMessage(), e);
            return mapStandingChargesResponse(new StandingChargesResponse(), agreement);
        }
    }

    public List<UnitRate> fetchAllUnitRates(Agreement agreement, String meterType, String rateType, String rateTypeLabel) {
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
