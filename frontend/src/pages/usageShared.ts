import { useEffect, useState } from 'react'
import { getMeterPoints, getStandingChargesByDay } from '../api/client'
import type { MeterPoint } from '../api/types'

export interface MpanFigures {
  kwh: number
  avgRate: number | null
  usageCost: number
  stdChg: number
  total: number
  // Off-peak kWh/cost, summed like kwh/usageCost rather than kept as a ratio - null-for-that-
  // period (no peak/off-peak split applicable) contributes 0, the same as "none of this period
  // was off-peak", so aggregate percentages stay meaningful across a mix of split/non-split
  // periods.
  kwhOffPeak: number
  costOffPeak: number
  // Total half-hour intervals this figure is derived from, and how many of them are
  // data-integrity-check placeholders (zero consumption, standing in for a half-hour Octopus
  // never reported) rather than real readings - see intervalCount/missingIntervalCount on the
  // API response. missingIntervalCount > 0 drives the table's "*" marker and its tooltip's "N
  // out of M periods" wording; both are summed (not averaged) by addFigures/averageFigures, so
  // a total or average always reports the true underlying counts, not a diluted fraction.
  intervalCount: number
  missingIntervalCount: number
}

// One row of a usage-by-X table/chart, generic across granularities - `key` is a
// chronologically-sortable string (e.g. "2026-08-05", "14:30") used for ordering and for the
// "beyond the latest period we have data for" cutoff; `label` is what's actually displayed in
// the table. `chartLabel` is what's shown on the chart's x-axis - usually the same as `label`,
// but a page can shorten it via UsagePeriodConfig.chartLabelForKey where the table has room to
// spell things out (e.g. "Jan 2026") but repeating that on every tick would be cramped/redundant
// (e.g. the year page, where the year is already shown in the page's own heading).
export interface PeriodRow {
  key: string
  label: string
  chartLabel: string
  byMpan: Record<string, MpanFigures>
}

export function emptyFigures(): MpanFigures {
  return {
    kwh: 0,
    avgRate: null,
    usageCost: 0,
    stdChg: 0,
    total: 0,
    kwhOffPeak: 0,
    costOffPeak: 0,
    intervalCount: 0,
    missingIntervalCount: 0,
  }
}

export function addFigures(a: MpanFigures, b: MpanFigures): MpanFigures {
  const kwh = a.kwh + b.kwh
  const usageCost = a.usageCost + b.usageCost
  const stdChg = a.stdChg + b.stdChg
  const kwhOffPeak = a.kwhOffPeak + b.kwhOffPeak
  const costOffPeak = a.costOffPeak + b.costOffPeak
  return {
    kwh,
    usageCost,
    stdChg,
    total: usageCost + stdChg,
    avgRate: kwh !== 0 ? usageCost / kwh : null,
    kwhOffPeak,
    costOffPeak,
    intervalCount: a.intervalCount + b.intervalCount,
    missingIntervalCount: a.missingIntervalCount + b.missingIntervalCount,
  }
}

// Averaging cost/kWh totals and re-deriving the rate from them gives the same rate as the
// total's rate (dividing both sides of cost/kWh by the same count cancels out), so the average
// rate is just the total's rate, unchanged.
export function averageFigures(f: MpanFigures, count: number): MpanFigures {
  if (count <= 0) return emptyFigures()
  return {
    kwh: f.kwh / count,
    avgRate: f.avgRate,
    usageCost: f.usageCost / count,
    stdChg: f.stdChg / count,
    total: f.total / count,
    kwhOffPeak: f.kwhOffPeak / count,
    costOffPeak: f.costOffPeak / count,
    // Not divided - these are counts of underlying half-hour periods, not amounts, so the
    // AVG row's tooltip should still report the real totals behind it, not a fractional count.
    intervalCount: f.intervalCount,
    missingIntervalCount: f.missingIntervalCount,
  }
}

// Off-peak % is derived from the summed kwh/kwhOffPeak at display time (like avgRate), not
// stored directly, so it stays consistent whether reading a single period or an aggregated row.
export function offPeakPct(f: MpanFigures): number | null {
  return f.kwh !== 0 ? (f.kwhOffPeak / f.kwh) * 100 : null
}

export function meterPointLabel(meterPoint: MeterPoint): string {
  if (meterPoint.meterType === 'GAS') return 'Gas'
  return meterPoint.isExport ? 'Elec (Export)' : 'Elec (Import)'
}

export function formatKwh(value: number): string {
  return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function formatCost(value: number): string {
  const formatted = Math.abs(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return value < 0 ? `-${formatted}` : formatted
}

export function formatRate(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '–'
  return value.toLocaleString(undefined, { minimumFractionDigits: 4, maximumFractionDigits: 4 })
}

export function formatPercent(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '–'
  return `${value.toLocaleString(undefined, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
}

export type ChartView = 'usage' | 'cost' | 'netUsage' | 'netCost'

export const CHART_VIEWS: { view: ChartView; label: string }[] = [
  { view: 'usage', label: 'kWh' },
  { view: 'cost', label: '£' },
  { view: 'netUsage', label: 'Net kWh' },
  { view: 'netCost', label: 'Net £' },
]

export function isNetView(view: ChartView): boolean {
  return view === 'netUsage' || view === 'netCost'
}

export function isKwhView(view: ChartView): boolean {
  return view === 'usage' || view === 'netUsage'
}

export function pad2(value: number): string {
  return value.toString().padStart(2, '0')
}

export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

export const MONTH_NAMES_SHORT = MONTH_NAMES.map((name) => name.slice(0, 3))

// Built from local calendar components (not parsed from an ISO string) so results are always
// correct for the intended calendar date regardless of the viewer's time zone.
export function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

// Matches H2's DATE_TRUNC('WEEK', ...) - the Monday on or before the given date (ISO-8601 week
// start).
export function isoWeekMonday(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  const weekday = date.getDay()
  const diff = weekday === 0 ? -6 : 1 - weekday
  date.setDate(date.getDate() + diff)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function dayOfWeek(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'short' })
}

export function formatDayLabel(dateStr: string): string {
  const [, monthPart, dayPart] = dateStr.split('-')
  const monthIndex = Number(monthPart) - 1
  return `${dayPart}-${MONTH_NAMES[monthIndex].slice(0, 3)}`
}

// Same as formatDayLabel but with a 2-digit year suffix, for views whose rows can span a year
// boundary (e.g. weeks) where "05-Jan" alone would be ambiguous.
export function formatDayLabelWithYear(dateStr: string): string {
  const [year] = dateStr.split('-')
  return `${formatDayLabel(dateStr)}-${year.slice(2)}`
}

// A reader-friendly full date ("10 Aug 2026"), for the Insights section's "Summary for X"
// heading on the half-hour view, which is scoped to a single calendar day.
export function formatFullDate(dateStr: string): string {
  const [year, month, day] = dateStr.split('-')
  return `${Number(day)} ${MONTH_NAMES_SHORT[Number(month) - 1]} ${year}`
}

const CURRENT_YEAR = new Date().getFullYear()
const BASE_YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => CURRENT_YEAR - i)

// The static range covers typical browsing, but prev/next navigation can walk outside it -
// keep the dropdown in sync with wherever navigation has actually landed.
export function yearOptions(selectedYear: number): number[] {
  if (BASE_YEAR_OPTIONS.includes(selectedYear)) return BASE_YEAR_OPTIONS
  return [...BASE_YEAR_OPTIONS, selectedYear].sort((a, b) => b - a)
}

export function useMeterPoints(): { meterPoints: MeterPoint[] | null; error: string | null } {
  const [meterPoints, setMeterPoints] = useState<MeterPoint[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMeterPoints()
      .then((points) => {
        if (cancelled) return
        setMeterPoints(points)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load meter points')
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { meterPoints, error }
}

export interface RawPeriodItem {
  key: string
  isExport: boolean
  kwh: number
  cost: number
  avgRate: number | null
  kwhOffPeak: number | null
  costOffPeak: number | null
  intervalCount: number
  missingIntervalCount: number
}

export interface UsagePeriodConfig {
  fromDate: string
  toDate: string
  // Rows to show even with zero usage/charges (e.g. every day in a month) - left empty for
  // open-ended views (like "by year") where only periods with actual data should appear.
  expectedKeys: string[]
  labelForKey: (key: string) => string
  // Overrides the chart's x-axis tick text - defaults to labelForKey when omitted. See
  // PeriodRow.chartLabel.
  chartLabelForKey?: (key: string) => string
  // Maps a standing-charge day (the daily standing-charge endpoint is the only granularity that
  // charge is recorded at) onto the period key(s) it should be attributed to - the day's amount
  // is split evenly across however many keys are returned (usually just one, but e.g. the
  // half-hour view spreads a single day's charge across all of that day's half-hour periods).
  bucketKeyForStandingChargeDate: (dateStr: string) => string[]
  fetchUsageItems: (mpan: string, fromDate: string, toDate: string) => Promise<RawPeriodItem[]>
}

export interface UsagePeriodData {
  rows: PeriodRow[] | null
  offPeakAvailableByMpan: Map<string, boolean> | null
  latestPeriodKeyByMpan: Map<string, string | null> | null
  // Calendar dates ("YYYY-MM-DD") a standing charge actually accrued on, per MPAN - independent
  // of the page's own row granularity, so a blended standing-charge rate can always be reported
  // per calendar day (matching how the charge itself accrues) even on a week/month/year/half-hour
  // page whose rows aren't one-per-day. Only dates that have "happened" (same cutoff as above) are
  // included.
  stdChgDaysByMpan: Map<string, Set<string>> | null
  error: string | null
}

// Shared by every usage-by-X page: fetches usage + standing charges for every meter point,
// buckets both into PeriodRow keyed by the granularity's period key, and applies the same
// "don't show a standing charge for a period beyond the latest one we actually have usage data
// for" cutoff that avoids implying a future/not-yet-happened period has already been charged.
// Callers MUST memoize `config` (e.g. via useMemo) - its identity is an effect dependency.
export function useUsagePeriodData(meterPoints: MeterPoint[] | null, config: UsagePeriodConfig): UsagePeriodData {
  const [rows, setRows] = useState<PeriodRow[] | null>(null)
  const [offPeakAvailableByMpan, setOffPeakAvailableByMpan] = useState<Map<string, boolean> | null>(null)
  const [latestPeriodKeyByMpan, setLatestPeriodKeyByMpan] = useState<Map<string, string | null> | null>(null)
  const [stdChgDaysByMpan, setStdChgDaysByMpan] = useState<Map<string, Set<string>> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!meterPoints) return
    let cancelled = false
    setError(null)
    setRows(null)

    Promise.all(
      meterPoints.map((mp) =>
        Promise.all([
          config.fetchUsageItems(mp.mpan, config.fromDate, config.toDate),
          getStandingChargesByDay(mp.mpan, config.fromDate, config.toDate),
        ]).then(([items, standingCharges]) => ({ mpan: mp.mpan, items, standingCharges })),
      ),
    )
      .then((perMpanResults) => {
        if (cancelled) return

        const chartLabelForKey = config.chartLabelForKey ?? config.labelForKey
        function buildRow(key: string): PeriodRow {
          return { key, label: config.labelForKey(key), chartLabel: chartLabelForKey(key), byMpan: {} }
        }

        const rowByKey = new Map<string, PeriodRow>()
        for (const key of config.expectedKeys) {
          rowByKey.set(key, buildRow(key))
        }
        function rowFor(key: string): PeriodRow {
          let row = rowByKey.get(key)
          if (!row) {
            row = buildRow(key)
            rowByKey.set(key, row)
          }
          return row
        }

        const offPeakAvailable = new Map<string, boolean>()
        const latestKeyByMpan = new Map<string, string | null>()
        const stdChgDays = new Map<string, Set<string>>()

        for (const { mpan, items, standingCharges } of perMpanResults) {
          const stdChgByKey = new Map<string, number>()
          for (const s of standingCharges) {
            const bucketKeys = config.bucketKeyForStandingChargeDate(s.chargeDate)
            const share = s.amount / bucketKeys.length
            for (const bucketKey of bucketKeys) {
              stdChgByKey.set(bucketKey, (stdChgByKey.get(bucketKey) ?? 0) + share)
            }
          }

          offPeakAvailable.set(mpan, items.some((item) => item.kwhOffPeak !== null))

          for (const item of items) {
            const row = rowFor(item.key)
            const stdChg = stdChgByKey.get(item.key) ?? 0
            // Exported energy earns money rather than costing it, so it's shown as negative
            // usage/cost - this makes the TOTAL column a genuine net figure.
            const sign = item.isExport ? -1 : 1
            const kwh = item.kwh * sign
            const usageCost = item.cost * sign
            row.byMpan[mpan] = {
              kwh,
              avgRate: item.avgRate,
              usageCost,
              stdChg,
              total: usageCost + stdChg,
              kwhOffPeak: item.kwhOffPeak !== null ? item.kwhOffPeak * sign : 0,
              costOffPeak: item.costOffPeak !== null ? item.costOffPeak * sign : 0,
              intervalCount: item.intervalCount,
              missingIntervalCount: item.missingIntervalCount,
            }
          }

          // A standing charge still applies to a period with zero consumption, so make sure
          // those periods aren't left without a figure just because there was no usage row.
          for (const [key, amount] of stdChgByKey) {
            const row = rowFor(key)
            if (row.byMpan[mpan]) continue
            row.byMpan[mpan] = { kwh: 0, avgRate: null, usageCost: 0, stdChg: amount, total: amount, kwhOffPeak: 0, costOffPeak: 0, intervalCount: 0, missingIntervalCount: 0 }
          }

          // Standing charges are typically known in advance, but usage data can lag behind
          // (e.g. a smart meter reading not in yet), so only treat a period as "happened" for
          // this MPAN up to the latest period we have usage data for. No usage data at all
          // means no period has happened yet.
          const latestKey = items.reduce<string | null>(
            (latest, item) => (latest === null || item.key > latest ? item.key : latest),
            null,
          )
          latestKeyByMpan.set(mpan, latestKey)
          for (const row of rowByKey.values()) {
            if (latestKey !== null && row.key <= latestKey) continue
            const f = row.byMpan[mpan]
            if (!f) continue
            row.byMpan[mpan] = { ...f, stdChg: 0, total: f.usageCost }
          }

          // Same "happened yet" cutoff as above, but tracked by actual calendar date rather than
          // by period key - a half-hour page's single day maps to 48 period keys via
          // bucketKeyForStandingChargeDate, which would otherwise make its charge look like it
          // spans 48 days instead of 1.
          const chargedDays = new Set<string>()
          for (const s of standingCharges) {
            if (s.amount === 0) continue
            const bucketKeys = config.bucketKeyForStandingChargeDate(s.chargeDate)
            const hasHappened = latestKey !== null && bucketKeys.some((k) => k <= latestKey)
            if (hasHappened) chargedDays.add(s.chargeDate)
          }
          stdChgDays.set(mpan, chargedDays)
        }

        setOffPeakAvailableByMpan(offPeakAvailable)
        setLatestPeriodKeyByMpan(latestKeyByMpan)
        setStdChgDaysByMpan(stdChgDays)
        setRows(Array.from(rowByKey.values()).sort((a, b) => a.key.localeCompare(b.key)))
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load usage data')
      })

    return () => {
      cancelled = true
    }
  }, [meterPoints, config])

  return { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, stdChgDaysByMpan, error }
}
