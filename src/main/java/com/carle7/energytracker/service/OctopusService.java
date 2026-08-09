package com.carle7.energytracker.service;

import com.carle7.energytracker.model.Agreement;
import com.carle7.energytracker.model.DayAndNightTariff;
import com.carle7.energytracker.model.Meter;
import com.carle7.energytracker.model.MeterPoint;
import com.carle7.energytracker.model.StandingCharge;
import com.carle7.energytracker.model.StandingChargeByDay;
import com.carle7.energytracker.model.UnitRate;
import com.carle7.energytracker.model.UnitRateByHalfHour;
import com.carle7.energytracker.model.Usage;
import com.carle7.energytracker.model.UtcToLocal;
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
import java.math.RoundingMode;
import java.time.LocalDate;
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
import java.util.Set;

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

    @Autowired
    private StandingChargeByDayRepository standingChargeByDayRepository;

    @Autowired
    private UtcToLocalRepository utcToLocalRepository;

    private static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");

    // Octopus reports gas consumption in cubic metres; convert to kWh using the standard Ofgem
    // formula. The calorific value is a fixed approximation (Ofgem's own consumer-tool placeholder)
    // since the actual daily, region-specific value isn't available from the consumption API.
    private static final BigDecimal GAS_VOLUME_CORRECTION_FACTOR = new BigDecimal("1.02264");
    private static final BigDecimal GAS_CALORIFIC_VALUE_MJ_PER_M3 = new BigDecimal("40.0");
    private static final BigDecimal MEGAJOULES_PER_KWH = new BigDecimal("3.6");

    @Transactional
    public AccountLoadResult loadAccountData(boolean deleteAll) {
        AccountLoadResult result = new AccountLoadResult();
        try {
            if (deleteAll) {
                // Order matters because of FK constraints.
                unitRateByHalfHourRepository.deleteAllInBatch();
                standingChargeByDayRepository.deleteAllInBatch();
                unitRateRepository.deleteAllInBatch();
                standingChargeRepository.deleteAllInBatch();
                agreementRepository.deleteAllInBatch();
                meterRepository.deleteAllInBatch();
                meterPointRepository.deleteAllInBatch();
            }

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

            // Upsert meter points by mpan, so re-running without deleteAll updates existing rows
            // instead of colliding with their unique constraint.
            long startTime = System.currentTimeMillis();
            List<MeterPoint> savedMeterPoints = new ArrayList<>();
            for (MeterPointData mpd : meterPointsData) {
                MeterPoint incoming = mpd.meterPoint();
                MeterPoint toSave = meterPointRepository.findByMpan(incoming.getMpan())
                        .map(existing -> {
                            existing.setIsExport(incoming.getIsExport());
                            existing.setMeterType(incoming.getMeterType());
                            return existing;
                        })
                        .orElse(incoming);
                savedMeterPoints.add(meterPointRepository.save(toSave));
            }
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} meter point records in {} ms", savedMeterPoints.size(), durationMs);
            result.setMeterPointCount(savedMeterPoints.size());

            // Update meterPointsData with saved meter point IDs
            for (int i = 0; i < savedMeterPoints.size(); i++) {
                meterPointsData.get(i).meterPoint().setId(savedMeterPoints.get(i).getId());
            }

            // Upsert meters, one per physical meter on each meter point; skip ones already on record.
            startTime = System.currentTimeMillis();
            List<Meter> savedMeters = new ArrayList<>();
            for (MeterPointData mpd : meterPointsData) {
                for (MeterDetailDto meterDto : mpd.meterDtos()) {
                    Meter meter = meterRepository
                            .findByMeterPointIdAndSerialNumber(mpd.meterPoint().getId(), meterDto.serial_number)
                            .orElseGet(() -> meterRepository.save(new Meter(meterDto.serial_number, mpd.meterPoint().getId())));
                    savedMeters.add(meter);
                }
            }
            durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} meter records in {} ms", savedMeters.size(), durationMs);
            result.setMeterCount(savedMeters.size());

            // Upsert agreements per meter point (deduplicating by tariff_code/valid_from within each
            // meter point); an existing agreement only has its valid_to refreshed, since an
            // open-ended agreement (valid_to null) becomes closed once superseded.
            startTime = System.currentTimeMillis();
            List<Agreement> savedAgreements = new ArrayList<>();
            for (MeterPointData mpd : meterPointsData) {
                var uniqueAgreementDtos = new java.util.LinkedHashMap<String, AgreementDetailDto>();
                for (AgreementDetailDto dto : mpd.agreementDtos()) {
                    uniqueAgreementDtos.putIfAbsent(dto.tariff_code + "|" + dto.valid_from, dto);
                }
                for (AgreementDetailDto dto : uniqueAgreementDtos.values()) {
                    LocalDateTime validFrom = parseDateTime(dto.valid_from);
                    LocalDateTime validTo = dto.valid_to != null ? parseDateTime(dto.valid_to) : null;
                    Agreement toSave = agreementRepository
                            .findByMeterPointIdAndTariffCodeAndValidFrom(mpd.meterPoint().getId(), dto.tariff_code, validFrom)
                            .map(existing -> {
                                existing.setValidTo(validTo);
                                return existing;
                            })
                            .orElseGet(() -> new Agreement(dto.tariff_code, validFrom, validTo, mpd.meterPoint().getId()));
                    savedAgreements.add(agreementRepository.save(toSave));
                }
            }
            durationMs = System.currentTimeMillis() - startTime;
            logger.info("Saved {} agreement records in {} ms", savedAgreements.size(), durationMs);
            result.setAgreementCount(savedAgreements.size());

            // Build a map from meter point ID to meter type for standing charge / unit rate lookups
            var meterTypeByMeterPointId = new java.util.HashMap<Long, String>();
            for (MeterPointData mpd : meterPointsData) {
                meterTypeByMeterPointId.put(mpd.meterPoint().getId(), mpd.meterPoint().getMeterType());
            }

            // Load standing charges and unit rates for each agreement. Both are appended-only
            // history (a past valid_from/payment_method/rate_type combination never changes), so a
            // re-run only needs to insert whatever isn't already on record; the reported counts are
            // the totals now held for the agreement, existing plus newly inserted.
            int standingChargeCount = 0;
            int unitRateCount = 0;
            for (Agreement agreement : savedAgreements) {
                String meterType = meterTypeByMeterPointId.getOrDefault(agreement.getMeterPointId(), "ELEC");

                Set<String> existingStandingChargeKeys = standingChargeKeys(agreement.getId());
                var newStandingCharges = octopusApiService.fetchStandingCharges(agreement, meterType).stream()
                        .filter(sc -> !existingStandingChargeKeys.contains(standingChargeKey(sc)))
                        .toList();
                standingChargeRepository.saveAll(newStandingCharges);
                standingChargeCount += existingStandingChargeKeys.size() + newStandingCharges.size();

                // Check if this is a day-and-night tariff
                boolean isDayAndNightTariff = dayAndNightTariffRepository
                        .findByTariffCode(agreement.getTariffCode())
                        .isPresent();

                Set<String> existingUnitRateKeys = unitRateKeys(agreement.getId());

                if (isDayAndNightTariff) {
                    // Load day and night rates from separate endpoints
                    var dayRates = octopusApiService.fetchAllUnitRates(agreement, meterType, "day", "DAY").stream()
                            .filter(r -> !existingUnitRateKeys.contains(unitRateKey(r)))
                            .toList();
                    unitRateRepository.saveAll(dayRates);
                    unitRateCount += dayRates.size();

                    var nightRates = octopusApiService.fetchAllUnitRates(agreement, meterType, "night", "NIGHT").stream()
                            .filter(r -> !existingUnitRateKeys.contains(unitRateKey(r)))
                            .toList();
                    unitRateRepository.saveAll(nightRates);
                    unitRateCount += existingUnitRateKeys.size() + dayRates.size() + nightRates.size();
                } else {
                    // Load standard rates
                    var unitRates = octopusApiService.fetchAllUnitRates(agreement, meterType, "standard", "STANDARD").stream()
                            .filter(r -> !existingUnitRateKeys.contains(unitRateKey(r)))
                            .toList();
                    unitRateRepository.saveAll(unitRates);
                    unitRateCount += existingUnitRateKeys.size() + unitRates.size();
                }
            }
            result.setStandingChargeCount(standingChargeCount);
            result.setUnitRateCount(unitRateCount);
            result.setUnitRatesByHalfHourCount(this.populateHalfHourlyUnitRates());
            result.setStandingChargesByDayCount(this.populateDailyStandingCharges());
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
        private int standingChargesByDayCount;
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

        public int getStandingChargesByDayCount() {
            return standingChargesByDayCount;
        }

        public void setStandingChargesByDayCount(int standingChargesByDayCount) {
            this.standingChargesByDayCount = standingChargesByDayCount;
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

    private Set<String> standingChargeKeys(Long agreementId) {
        return standingChargeRepository.findByAgreementIdOrderByValidFrom(agreementId).stream()
                .map(this::standingChargeKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String standingChargeKey(StandingCharge standingCharge) {
        return standingCharge.getPaymentMethod() + "|" + standingCharge.getValidFrom();
    }

    private Set<String> unitRateKeys(Long agreementId) {
        return unitRateRepository.findByAgreementIdOrderByValidFrom(agreementId).stream()
                .map(this::unitRateKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String unitRateKey(UnitRate unitRate) {
        return unitRate.getPaymentMethod() + "|" + unitRate.getValidFrom() + "|" + unitRate.getRateType();
    }

    public UsageLoadResult loadUsageData(boolean deleteAll) {
        UsageLoadResult result = new UsageLoadResult();
        try {
            if (deleteAll) {
                usageRepository.deleteAllInBatch();
            }

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
                                        toKwh(meterPoint.getMeterType(), data.consumption),
                                        meterPoint.getMpan()
                                ))
                                .toList();
                        usageRepository.saveAll(usages);
                        usageCount += usages.size();
                    }
                }
            }
            result.setUsageCount(usageCount);
            result.setUtcToLocalCount(this.populateUtcToLocalMapping());
        } catch (Exception e) {
            logger.error("Failed to load usage data: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        return result;
    }

    /**
     * Gas consumption arrives from Octopus in cubic metres; electricity consumption is already kWh.
     */
    private BigDecimal toKwh(String meterType, double consumption) {
        BigDecimal raw = BigDecimal.valueOf(consumption);
        if (!"GAS".equals(meterType)) {
            return raw;
        }
        return raw.multiply(GAS_VOLUME_CORRECTION_FACTOR)
                .multiply(GAS_CALORIFIC_VALUE_MJ_PER_M3)
                .divide(MEGAJOULES_PER_KWH, 4, RoundingMode.HALF_UP);
    }

    public static class UsageLoadResult {
        private int usageCount;
        private int utcToLocalCount;
        private String error;

        public int getUsageCount() {
            return usageCount;
        }

        public void setUsageCount(int usageCount) {
            this.usageCount = usageCount;
        }

        public int getUtcToLocalCount() {
            return utcToLocalCount;
        }

        public void setUtcToLocalCount(int utcToLocalCount) {
            this.utcToLocalCount = utcToLocalCount;
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
        // Fully derived from unit_rate, so it's always safe (and necessary, to stay idempotent
        // across repeated loadAccountData calls) to recompute from scratch rather than upsert.
        unitRateByHalfHourRepository.deleteAllInBatch();

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
                    .filter(r -> r.getValidFrom().isBefore(windowEnd))
                    .toList();

            Map<String, List<UnitRate>> series = groupIntoSeries(rates);

            Optional<DayAndNightTariff> dnt = dayAndNightTariffRepository.findByTariffCode(agreement.getTariffCode());

            List<UnitRateByHalfHour> agreementSlots = new ArrayList<>();
            if (dnt.isEmpty()) {
                for (List<UnitRate> s : series.values()) {
                    agreementSlots.addAll(expandSeriesToHalfHourSlots(s, agreement.getValidFrom(), windowEnd));
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

    private List<UnitRateByHalfHour> expandSeriesToHalfHourSlots(List<UnitRate> series, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<UnitRateByHalfHour> slots = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            UnitRate rate = series.get(i);
            LocalDateTime rawEndBound = i + 1 < series.size()
                    ? series.get(i + 1).getValidFrom()
                    : (rate.getValidTo() != null ? rate.getValidTo() : windowEnd);
            // Never generate slots past the agreement's own window, even if the unit_rate
            // record's own valid_to extends further (e.g. a superseded price-change record).
            LocalDateTime endBound = rawEndBound.isBefore(windowEnd) ? rawEndBound : windowEnd;

            // Never generate slots before the agreement's own window, even if the unit_rate
            // record's own valid_from starts earlier (e.g. a price change that predates the agreement).
            LocalDateTime slot = rate.getValidFrom().isBefore(windowStart) ? windowStart : rate.getValidFrom();
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
            for (UnitRateByHalfHour halfHour : expandSeriesToHalfHourSlots(entry.getValue(), agreement.getValidFrom(), windowEnd)) {
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

    /**
     * Standing charges apply per local (Europe/London) calendar day, even though everything is
     * stored in UTC. So a day's valid_from is the UTC instant of local midnight for that day:
     * 23:00 the previous day during BST, 00:00 the same day during GMT.
     */
    @Transactional
    public int populateDailyStandingCharges() {
        // Fully derived from standing_charge, so it's always safe (and necessary, to stay
        // idempotent across repeated loadAccountData calls) to recompute from scratch rather than upsert.
        standingChargeByDayRepository.deleteAllInBatch();

        List<Agreement> agreements = agreementRepository.findAll();
        // Keyed by the same tuple as STANDING_CHARGE_BY_DAY's unique constraint, so overlapping
        // source data (e.g. superseded standing_charge periods) can never produce a duplicate insert.
        Map<String, StandingChargeByDay> daysByKey = new LinkedHashMap<>();
        int duplicatesSkipped = 0;

        for (Agreement agreement : agreements) {
            LocalDateTime windowEnd = agreement.getValidTo() != null
                    ? agreement.getValidTo()
                    : LocalDateTime.now().plusDays(90);

            List<StandingCharge> charges = standingChargeRepository.findByAgreementIdOrderByValidFrom(agreement.getId()).stream()
                    .filter(c -> c.getValidFrom().isBefore(windowEnd))
                    .toList();

            Map<String, List<StandingCharge>> series = groupStandingChargesByPaymentMethod(charges);

            for (List<StandingCharge> s : series.values()) {
                for (StandingChargeByDay day : expandSeriesToDailySlots(s, agreement.getValidFrom(), windowEnd)) {
                    String key = day.getAgreementId() + "|" + day.getValidFrom() + "|" + day.getPaymentMethod();
                    if (daysByKey.putIfAbsent(key, day) != null) {
                        duplicatesSkipped++;
                    }
                }
            }
        }

        if (duplicatesSkipped > 0) {
            logger.warn("Skipped {} duplicate daily standing charge slots (overlapping standing_charge periods)", duplicatesSkipped);
        }

        long startTime = System.currentTimeMillis();
        List<StandingChargeByDay> saved = standingChargeByDayRepository.saveAll(new ArrayList<>(daysByKey.values()));
        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Saved {} daily standing charge records in {} ms", saved.size(), durationMs);

        return saved.size();
    }

    private Map<String, List<StandingCharge>> groupStandingChargesByPaymentMethod(List<StandingCharge> charges) {
        Map<String, List<StandingCharge>> series = new LinkedHashMap<>();
        for (StandingCharge charge : charges) {
            series.computeIfAbsent(charge.getPaymentMethod(), k -> new ArrayList<>()).add(charge);
        }
        return series;
    }

    private List<StandingChargeByDay> expandSeriesToDailySlots(List<StandingCharge> series, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<StandingChargeByDay> days = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            StandingCharge charge = series.get(i);
            LocalDateTime rawEndBound = i + 1 < series.size()
                    ? series.get(i + 1).getValidFrom()
                    : (charge.getValidTo() != null ? charge.getValidTo() : windowEnd);
            // Never generate days past the agreement's own window, even if the standing_charge
            // record's own valid_to extends further (e.g. a superseded price-change record).
            LocalDateTime endBound = rawEndBound.isBefore(windowEnd) ? rawEndBound : windowEnd;

            // Never generate days before the agreement's own window, even if the standing_charge
            // record's own valid_from starts earlier (e.g. a price change that predates the agreement).
            LocalDateTime chargeStart = charge.getValidFrom().isBefore(windowStart) ? windowStart : charge.getValidFrom();

            LocalDate day = londonDateOf(chargeStart);
            LocalDateTime daySlot = londonMidnightUtc(day);
            // A local day whose UTC midnight instant falls before chargeStart is only partially
            // covered by this record; if it's not the window's own first day, the day it belongs
            // to already got its slot from an earlier record in this series, so roll forward to
            // the first fully-covered day. The window's first day is never rolled past, even when
            // windowStart itself (e.g. an agreement boundary recorded in UTC) falls mid-day rather
            // than on a local-midnight boundary - nothing else will supply that day otherwise,
            // which otherwise silently drops it (e.g. around a BST/GMT-adjacent agreement switch).
            LocalDate windowStartDay = londonDateOf(windowStart);
            while (daySlot.isBefore(chargeStart) && day.isAfter(windowStartDay)) {
                day = day.plusDays(1);
                daySlot = londonMidnightUtc(day);
            }

            while (daySlot.isBefore(endBound)) {
                LocalDate nextDay = day.plusDays(1);
                LocalDateTime nextDaySlot = londonMidnightUtc(nextDay);
                days.add(new StandingChargeByDay(
                        charge.getAgreementId(),
                        charge.getValueExcVat(),
                        charge.getValueIncVat(),
                        daySlot,
                        nextDaySlot,
                        charge.getPaymentMethod()
                ));
                day = nextDay;
                daySlot = nextDaySlot;
            }
        }
        return days;
    }

    private LocalDate londonDateOf(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(LONDON_ZONE).toLocalDate();
    }

    private LocalDateTime londonMidnightUtc(LocalDate londonDate) {
        return londonDate.atStartOfDay(LONDON_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Populates UTC_TO_LOCAL with one row per half-hour slot covering the full span of
     * UNIT_RATE_BY_HALF_HOUR data, mapping each UTC instant to its Europe/London local time and
     * the GMT/BST abbreviation in effect at that instant, for joining against other UTC-keyed tables.
     * The range is floored to local midnight of the earliest slot's local day, so the first
     * record always has a local_time of 00:00:00.
     */
    @Transactional
    public int populateUtcToLocalMapping() {
        Optional<UnitRateByHalfHour> earliest = unitRateByHalfHourRepository.findFirstByOrderByValidFromAsc();
        Optional<UnitRateByHalfHour> latest = unitRateByHalfHourRepository.findFirstByOrderByValidFromDesc();

        if (earliest.isEmpty() || latest.isEmpty()) {
            return 0;
        }

        LocalDateTime start = londonMidnightUtc(londonDateOf(earliest.get().getValidFrom()));
        LocalDateTime end = latest.get().getValidTo();

        utcToLocalRepository.deleteAllInBatch();

        List<UtcToLocal> mappings = new ArrayList<>();
        LocalDateTime slot = start;
        while (slot.isBefore(end)) {
            mappings.add(new UtcToLocal(slot, londonLocalTimeOf(slot), londonZoneAbbreviation(slot)));
            slot = slot.plusMinutes(30);
        }

        long startTime = System.currentTimeMillis();
        List<UtcToLocal> saved = utcToLocalRepository.saveAll(mappings);
        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Saved {} UTC-to-local mapping records in {} ms", saved.size(), durationMs);

        return saved.size();
    }

    private LocalDateTime londonLocalTimeOf(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(LONDON_ZONE).toLocalDateTime();
    }

    private String londonZoneAbbreviation(LocalDateTime utc) {
        boolean isDst = LONDON_ZONE.getRules().isDaylightSavings(utc.toInstant(ZoneOffset.UTC));
        return isDst ? "BST" : "GMT";
    }

}

