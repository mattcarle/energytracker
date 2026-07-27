package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.UnitRate;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.repository.*;
import com.carle7.energytracker.service.OctopusApiService.AccountResponse;
import com.carle7.energytracker.service.OctopusApiService.AgreementDetailDto;
import com.carle7.energytracker.service.OctopusApiService.ConsumptionResponse;
import com.carle7.energytracker.service.OctopusApiService.MeterDetailDto;
import com.carle7.energytracker.service.OctopusApiService.MeterPointDto;
import com.carle7.energytracker.service.OctopusApiService.PropertyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Service
public class OctopusService {

    private static final Logger logger = LoggerFactory.getLogger(OctopusService.class);

    @Autowired
    private OctopusApiService octopusApiService;

    @Autowired
    private UsageRepository usageRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private AgreementRepository agreementRepository;

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

    @Transactional
    public AccountLoadResult loadAccountData() {
        AccountLoadResult result = new AccountLoadResult();
        try {
            // Clear existing data before reloading from Octopus API. Order matters because of FK constraints.
            unitRateByHalfHourRepository.deleteAllInBatch();
            unitRateRepository.deleteAllInBatch();
            standingChargeRepository.deleteAllInBatch();
            agreementRepository.deleteAllInBatch();
            meterRepository.deleteAllInBatch();
            meterPointRepository.deleteAllInBatch();

            AccountResponse accountResponse = octopusApiService.fetchAccountData();
            if (accountResponse == null) {
                logger.error("Failed to load account details: API returned null response");
                result.setError("API returned null response");
                return result;
            }

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

                var standingCharges = octopusApiService.fetchStandingCharges(agreement, meterType);
                standingChargeRepository.saveAll(standingCharges);
                standingChargeCount += standingCharges.size();

                // Check if this is a day-and-night tariff
                boolean isDayAndNightTariff = dayAndNightTariffRepository
                        .findByTariffCode(agreement.getTariffCode())
                        .isPresent();

                if (isDayAndNightTariff) {
                    // Load day and night rates from separate endpoints
                    var dayRates = octopusApiService.fetchAllUnitRates(agreement, meterType, "day", "DAY");
                    unitRateRepository.saveAll(dayRates);
                    unitRateCount += dayRates.size();

                    var nightRates = octopusApiService.fetchAllUnitRates(agreement, meterType, "night", "NIGHT");
                    unitRateRepository.saveAll(nightRates);
                    unitRateCount += nightRates.size();
                } else {
                    // Load standard rates
                    var unitRates = octopusApiService.fetchAllUnitRates(agreement, meterType, "standard", "STANDARD");
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

    private LocalDateTime parseDateTime(String dateTimeString) {
        return OffsetDateTime.parse(dateTimeString).toLocalDateTime();
    }

    public UsageLoadResult loadUsageData() {
        UsageLoadResult result = new UsageLoadResult();
        try {
            LocalDateTime periodFrom = usageRepository.findFirstByOrderByIntervalToDesc()
                    .map(Usage::getIntervalTo)
                    .or(() -> agreementRepository.findFirstByOrderByValidFromAsc().map(Agreement::getValidFrom))
                    .orElse(null);

            if (periodFrom == null) {
                logger.warn("No existing usage data and no agreements found; skipping usage load");
                return result;
            }

            LocalDateTime periodTo = LocalDateTime.now();
            if (!periodFrom.isBefore(periodTo)) {
                return result;
            }

            int usageCount = 0;
            for (MeterPoint meterPoint : meterPointRepository.findAll()) {
                for (Meter meter : meterRepository.findByMeterPointId(meterPoint.getId())) {
                    ConsumptionResponse response = octopusApiService.fetchConsumptionData(
                            meterPoint.getMeterType(), meterPoint.getMpan(), meter.getSerialNumber(), periodFrom, periodTo);
                    if (response == null) {
                        logger.error("Failed to load usage data for mpan {} meter {}", meterPoint.getMpan(), meter.getSerialNumber());
                        continue;
                    }

                    if (response.results != null) {
                        List<Usage> usages = response.results.stream()
                                .map(data -> new Usage(
                                        data.getIntervalStartAsLocalDateTime(),
                                        data.getIntervalEndAsLocalDateTime(),
                                        new BigDecimal(data.consumption),
                                        meterPoint.getMpan()
                                ))
                                .toList();
                        usageRepository.saveAll(usages);
                        usageCount += usages.size();
                    }
                }
            }
            result.setUsageCount(usageCount);
        } catch (Exception e) {
            logger.error("Failed to load usage data: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        return result;
    }

    public static class UsageLoadResult {
        private int usageCount;
        private String error;

        public int getUsageCount() {
            return usageCount;
        }

        public void setUsageCount(int usageCount) {
            this.usageCount = usageCount;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
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

}

