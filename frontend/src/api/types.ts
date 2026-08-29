export interface MeterPoint {
  id: number
  mpan: string
  isExport: boolean
  meterType: string
  createdAt: string
}

export interface Meter {
  id: number
  serialNumber: string
  meterPointId: number
  createdAt: string
}

export interface Agreement {
  id: number
  tariffCode: string
  validFrom: string
  validTo: string | null
  meterPointId: number
  createdAt: string
}

export interface UsageDayAggregate {
  mpan: string
  meterType: string
  isExport: boolean
  usageDate: string
  intervalCount: number
  missingIntervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
}

export interface UsageTotals {
  intervalCount: number
  missingIntervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
}

export interface UsageByDayResponse {
  days: UsageDayAggregate[]
  totals: UsageTotals
}

export interface UsageHalfHourAggregate {
  mpan: string
  meterType: string
  isExport: boolean
  usageInterval: string
  intervalCount: number
  missingIntervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
}

export interface UsageByHalfHourResponse {
  halfHours: UsageHalfHourAggregate[]
  totals: UsageTotals
}

export interface UsageWeekAggregate {
  mpan: string
  meterType: string
  isExport: boolean
  usageWeek: string
  intervalCount: number
  missingIntervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
}

export interface UsageByWeekResponse {
  weeks: UsageWeekAggregate[]
  totals: UsageTotals
}

export interface UsageMonthAggregate {
  mpan: string
  meterType: string
  isExport: boolean
  usageMonth: string
  intervalCount: number
  missingIntervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
}

export interface UsageByMonthResponse {
  months: UsageMonthAggregate[]
  totals: UsageTotals
}

export interface UsageYearAggregate {
  mpan: string
  meterType: string
  isExport: boolean
  usageYear: string
  intervalCount: number
  missingIntervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
}

export interface UsageByYearResponse {
  years: UsageYearAggregate[]
  totals: UsageTotals
}

export interface StandingChargeByDayEntry {
  chargeDate: string
  amount: number
}

export interface UsageDateRange {
  mpan: string
  earliest: string
  latest: string
}

export type UserRole = 'ADMIN' | 'USER'

export interface AuthUser {
  id: number
  username: string
  role: UserRole
  mustChangePassword: boolean
  createdAt: string
}

export interface AccountLoadResult {
  meterPointCount: number
  meterCount: number
  agreementCount: number
  standingChargeCount: number
  unitRateCount: number
  unitRatesByHalfHourCount: number
  standingChargesByDayCount: number
  error: string | null
}

export interface UsageLoadResult {
  usageCount: number
  utcToLocalCount: number
  error: string | null
}

export interface PlantLoadResult {
  plantId: string | null
  plantName: string | null
  installDate: string | null
  error: string | null
}

export interface SolarLoadResult {
  dayCount: number
  error: string | null
}

export interface GrowattCredentialsStatus {
  configured: boolean
  plantId: string | null
}

export interface SolarDayEntry {
  date: string
  kwh: number
}

// Reused for month/year rows - only the granularity of `period` differs (first of month / of year).
export interface SolarPeriodEntry {
  period: string
  kwh: number
}

export interface SolarTotals {
  periodCount: number
  kwh: number
}

export interface SolarByDayResponse {
  days: SolarDayEntry[]
  totals: SolarTotals
}

export interface SolarByMonthResponse {
  months: SolarPeriodEntry[]
  totals: SolarTotals
}

export interface SolarPowerPoint {
  time: string
  powerWatts: number | null
}

export interface SolarHourlyResponse {
  points: SolarPowerPoint[]
}

export interface SolarDateRange {
  plantId: string
  earliest: string
  latest: string
}

export interface SetupResult {
  user: AuthUser
  accountLoad: AccountLoadResult
  usageLoad: UsageLoadResult
  integrityReport: DataIntegrityReport
  // Both null when the Growatt step of the wizard was skipped.
  plantLoad: PlantLoadResult | null
  solarLoad: SolarLoadResult | null
}

export interface DataIntegrityGap {
  from: string | null
  to: string | null
}

export interface IntegrityCheckResult {
  earliest: string | null
  latest: string | null
  gaps: DataIntegrityGap[]
}

export interface MpanIntegrityReport {
  mpan: string
  meterType: string
  isExport: boolean
  agreements: IntegrityCheckResult
  standingCharges: IntegrityCheckResult
  unitRates: IntegrityCheckResult
  usage: IntegrityCheckResult
}

export interface DataIntegrityReport {
  mpans: MpanIntegrityReport[]
}

export interface DayAndNightTariffStatus {
  id: number | null
  tariffCode: string
  dayRateValidFrom: string | null
  nightRateValidFrom: string | null
}
