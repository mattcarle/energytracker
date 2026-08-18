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
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
}

export interface UsageTotals {
  intervalCount: number
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

export interface SetupResult {
  user: AuthUser
  accountLoad: AccountLoadResult
  usageLoad: UsageLoadResult
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
