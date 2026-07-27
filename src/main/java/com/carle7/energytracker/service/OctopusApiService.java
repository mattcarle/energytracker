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
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    public ConsumptionResponse fetchConsumptionData(String meterType, String mpan, String meterSerial, LocalDateTime periodFrom, LocalDateTime periodTo) {
        String meterPointsSegment = "GAS".equals(meterType) ? "gas-meter-points" : "electricity-meter-points";
        String initialUrl = String.format(
                "%s/%s/%s/meters/%s/consumption/?period_from=%s&period_to=%s&page_size=25000",
                octopusConfig.getBaseUrl(),
                meterPointsSegment,
                mpan,
                meterSerial,
                periodFrom.atOffset(ZoneOffset.UTC).toString(),
                periodTo.atOffset(ZoneOffset.UTC).toString()
        );

        String basicAuth = Base64.getEncoder().encodeToString((octopusConfig.getAuthToken() + ":").getBytes());

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Basic " + basicAuth);

        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        List<ConsumptionDto> allResults = new ArrayList<>();
        String nextUrl = initialUrl;
        boolean firstPage = true;

        while (nextUrl != null) {
            try {
                long startTime = System.currentTimeMillis();
                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        URI.create(nextUrl),
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        String.class
                );
                long durationMs = System.currentTimeMillis() - startTime;
                logger.info("GET {} completed in {} ms", nextUrl, durationMs);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    logger.error("API error from {}: {} {}", nextUrl, response.getStatusCode(), response.getBody());
                    if (firstPage) return null;
                    break;
                }

                ConsumptionResponse pageResponse;
                try {
                    pageResponse = objectMapper.readValue(response.getBody(), ConsumptionResponse.class);
                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse consumption response from {}: {}", nextUrl, e.getMessage(), e);
                    if (firstPage) return null;
                    break;
                }

                if (pageResponse.results != null) {
                    allResults.addAll(pageResponse.results);
                }
                nextUrl = pageResponse.next;
                firstPage = false;
            } catch (Exception e) {
                logger.error("Failed to fetch consumption from {}: {}", nextUrl, e.getMessage(), e);
                if (firstPage) return null;
                break;
            }
        }

        ConsumptionResponse combined = new ConsumptionResponse();
        combined.count = allResults.size();
        combined.results = allResults;
        return combined;
    }

    public AccountResponse fetchAccountData() {
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
                    URI.create(url),
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

            try {
                return objectMapper.readValue(response.getBody(), AccountResponse.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse account details response from {}: {}", url, e.getMessage(), e);
                return null;
            }
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
                    URI.create(url),
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
                        URI.create(nextUrl),
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
}
