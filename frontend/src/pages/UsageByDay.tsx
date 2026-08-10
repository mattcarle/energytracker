import { Fragment, useEffect, useMemo, useState } from 'react'
import { getMeterPoints, getStandingChargesByDay, getUsageByDay } from '../api/client'
import type { MeterPoint } from '../api/types'
import UsageBarChart, { type ChartMetric } from '../components/UsageBarChart'
import './UsageByDay.css'

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => CURRENT_YEAR - i)

export interface MpanFigures {
  kwh: number
  avgRate: number | null
  usageCost: number
  stdChg: number
  total: number
  // Off-peak kWh, summed like kwh/usageCost rather than kept as a ratio - null-for-that-day
  // (no peak/off-peak split applicable) contributes 0, the same as "none of this day was
  // off-peak", so aggregate percentages stay meaningful across a mix of split/non-split days.
  kwhOffPeak: number
}

export interface DayRow {
  date: string
  byMpan: Record<string, MpanFigures>
}

function emptyFigures(): MpanFigures {
  return { kwh: 0, avgRate: null, usageCost: 0, stdChg: 0, total: 0, kwhOffPeak: 0 }
}

function addFigures(a: MpanFigures, b: MpanFigures): MpanFigures {
  const kwh = a.kwh + b.kwh
  const usageCost = a.usageCost + b.usageCost
  const stdChg = a.stdChg + b.stdChg
  const kwhOffPeak = a.kwhOffPeak + b.kwhOffPeak
  return {
    kwh,
    usageCost,
    stdChg,
    total: usageCost + stdChg,
    avgRate: kwh !== 0 ? usageCost / kwh : null,
    kwhOffPeak,
  }
}

// Averaging cost/kWh totals and re-deriving the rate from them gives the same rate as the
// total row (dividing both sides of cost/kWh by the same day count cancels out), so the
// average rate is just the total's rate, unchanged.
function averageFigures(f: MpanFigures, days: number): MpanFigures {
  if (days <= 0) return emptyFigures()
  return {
    kwh: f.kwh / days,
    avgRate: f.avgRate,
    usageCost: f.usageCost / days,
    stdChg: f.stdChg / days,
    total: f.total / days,
    kwhOffPeak: f.kwhOffPeak / days,
  }
}

// Off-peak % is derived from the summed kwh/kwhOffPeak at display time (like avgRate), not
// stored directly, so it stays consistent whether reading a single day or an aggregated row.
function offPeakPct(f: MpanFigures): number | null {
  return f.kwh !== 0 ? (f.kwhOffPeak / f.kwh) * 100 : null
}

function pad2(value: number): string {
  return value.toString().padStart(2, '0')
}

function firstOfMonth(year: number, month: number): string {
  return `${year}-${pad2(month)}-01`
}

function firstOfNextMonth(year: number, month: number): string {
  return month === 12 ? `${year + 1}-01-01` : `${year}-${pad2(month + 1)}-01`
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

export function meterPointLabel(meterPoint: MeterPoint): string {
  if (meterPoint.meterType === 'GAS') return 'Gas'
  return meterPoint.isExport ? 'Electricity (Export)' : 'Electricity (Import)'
}

// Built from local calendar components (not parsed from an ISO string) so the weekday is
// always correct for the intended calendar date regardless of the viewer's time zone.
function dayOfWeek(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'short' })
}

export function formatDate(dateStr: string): string {
  const [, monthPart, dayPart] = dateStr.split('-')
  const monthIndex = Number(monthPart) - 1
  return `${dayPart}-${MONTH_NAMES[monthIndex].slice(0, 3)}`
}

function formatKwh(value: number): string {
  return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatCost(value: number): string {
  const formatted = Math.abs(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return value < 0 ? `-${formatted}` : formatted
}

function formatRate(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '–'
  return value.toLocaleString(undefined, { minimumFractionDigits: 4, maximumFractionDigits: 4 })
}

function formatPercent(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '–'
  return `${value.toLocaleString(undefined, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
}

export default function UsageByDay() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [meterPoints, setMeterPoints] = useState<MeterPoint[] | null>(null)
  const [selectedMpans, setSelectedMpans] = useState<Set<string> | null>(null)
  const [rows, setRows] = useState<DayRow[] | null>(null)
  const [latestUsageDateByMpan, setLatestUsageDateByMpan] = useState<Map<string, string | null> | null>(null)
  const [offPeakAvailableByMpan, setOffPeakAvailableByMpan] = useState<Map<string, boolean> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [showChart, setShowChart] = useState(true)
  const [showTable, setShowTable] = useState(true)
  const [chartMetric, setChartMetric] = useState<ChartMetric>('kwh')

  useEffect(() => {
    let cancelled = false
    getMeterPoints()
      .then((points) => {
        if (cancelled) return
        setMeterPoints(points)
        setSelectedMpans(
          (current) => current ?? new Set(points.filter((p) => p.meterType !== 'GAS').map((p) => p.mpan)),
        )
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load meter points')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!meterPoints) return
    let cancelled = false
    setError(null)

    const fromDate = firstOfMonth(year, month)
    const toDate = firstOfNextMonth(year, month)
    const totalDays = daysInMonth(year, month)

    Promise.all(
      meterPoints.map((meterPoint) =>
        Promise.all([
          getUsageByDay(meterPoint.mpan, fromDate, toDate),
          getStandingChargesByDay(meterPoint.mpan, fromDate, toDate),
        ]).then(([usageResponse, standingCharges]) => ({
          mpan: meterPoint.mpan,
          usageDays: usageResponse.days,
          standingCharges,
        })),
      ),
    )
      .then((perMpanResults) => {
        if (cancelled) return

        const rowByDate = new Map<string, DayRow>()
        for (let day = 1; day <= totalDays; day++) {
          const date = `${year}-${pad2(month)}-${pad2(day)}`
          rowByDate.set(date, { date, byMpan: {} })
        }

        const latestUsageDateByMpan = new Map<string, string | null>()
        const offPeakAvailableByMpan = new Map<string, boolean>()

        for (const { mpan, usageDays, standingCharges } of perMpanResults) {
          const stdChgByDate = new Map(standingCharges.map((s) => [s.chargeDate, s.amount]))

          offPeakAvailableByMpan.set(mpan, usageDays.some((day) => day.kwhOffPeak !== null))

          for (const day of usageDays) {
            const row = rowByDate.get(day.usageDate)
            if (!row) continue
            const stdChg = stdChgByDate.get(day.usageDate) ?? 0
            // Exported energy earns money rather than costing it, so it's shown as negative
            // usage/cost - this makes the TOTAL column a genuine net figure, going negative
            // on a day export outweighs import.
            const sign = day.isExport ? -1 : 1
            const kwh = day.kwh * sign
            const usageCost = day.cost * sign
            row.byMpan[mpan] = {
              kwh,
              avgRate: day.avgRate,
              usageCost,
              stdChg,
              total: usageCost + stdChg,
              kwhOffPeak: day.kwhOffPeak !== null ? day.kwhOffPeak * sign : 0,
            }
          }

          // A standing charge still applies on days with zero consumption, so make sure
          // those days aren't left without a figure just because there was no usage row.
          for (const [date, amount] of stdChgByDate) {
            const row = rowByDate.get(date)
            if (!row || row.byMpan[mpan]) continue
            row.byMpan[mpan] = { kwh: 0, avgRate: null, usageCost: 0, stdChg: amount, total: amount, kwhOffPeak: 0 }
          }

          // Standing charges are typically known in advance for the whole agreement, but
          // usage data can lag behind today (e.g. a smart meter reading not in yet), so only
          // treat a day as "happened" for this MPAN up to the latest day we have usage data
          // for, not simply up to today. No usage data at all means no day has happened yet.
          // The same cutoff is reused below to average over days-with-data rather than the
          // full month, for a part-way-through-the-month view.
          const latestUsageDate = usageDays.reduce<string | null>(
            (latest, day) => (latest === null || day.usageDate > latest ? day.usageDate : latest),
            null,
          )
          latestUsageDateByMpan.set(mpan, latestUsageDate)
          for (const row of rowByDate.values()) {
            if (latestUsageDate !== null && row.date <= latestUsageDate) continue
            const f = row.byMpan[mpan]
            if (!f) continue
            row.byMpan[mpan] = { ...f, stdChg: 0, total: f.usageCost }
          }
        }

        setLatestUsageDateByMpan(latestUsageDateByMpan)
        setOffPeakAvailableByMpan(offPeakAvailableByMpan)
        setRows(Array.from(rowByDate.values()).sort((a, b) => a.date.localeCompare(b.date)))
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load usage data')
      })

    return () => {
      cancelled = true
    }
  }, [year, month, meterPoints])

  // The static range covers typical browsing, but the prev/next month buttons can walk the
  // year outside it - keep the dropdown in sync with wherever navigation has actually landed.
  const yearOptions = useMemo(() => {
    if (YEAR_OPTIONS.includes(year)) return YEAR_OPTIONS
    return [...YEAR_OPTIONS, year].sort((a, b) => b - a)
  }, [year])

  const includedMeterPoints = useMemo(
    () => meterPoints?.filter((mp) => selectedMpans?.has(mp.mpan)) ?? [],
    [meterPoints, selectedMpans],
  )

  const totalsByMpan = useMemo(() => {
    if (!rows) return null
    const totals = new Map<string, MpanFigures>()
    for (const mp of meterPoints ?? []) {
      totals.set(mp.mpan, rows.reduce((acc, row) => addFigures(acc, row.byMpan[mp.mpan] ?? emptyFigures()), emptyFigures()))
    }
    return totals
  }, [rows, meterPoints])

  // Matches the standing-charge cutoff: for a part-way-through-the-month view, average over
  // the days we actually have usage data for rather than the whole month. A completed past
  // month naturally has data through its last day, so this has no effect there.
  function daysWithData(mpan: string): number {
    const latest = latestUsageDateByMpan?.get(mpan)
    if (!latest) return 0
    const dayOfMonth = Number(latest.split('-')[2])
    return rows ? Math.min(dayOfMonth, rows.length) : dayOfMonth
  }

  function grandTotal(byMpan: Record<string, MpanFigures>): { kwh: number; total: number } {
    let kwh = 0
    let total = 0
    for (const mp of includedMeterPoints) {
      const figures = byMpan[mp.mpan] ?? emptyFigures()
      kwh += figures.kwh
      total += figures.total
    }
    return { kwh, total }
  }

  function goToPreviousMonth() {
    if (month === 1) {
      setYear((y) => y - 1)
      setMonth(12)
    } else {
      setMonth((m) => m - 1)
    }
  }

  function goToNextMonth() {
    if (month === 12) {
      setYear((y) => y + 1)
      setMonth(1)
    } else {
      setMonth((m) => m + 1)
    }
  }

  // Refuses to turn a view off if it's the only one currently on, so the user can never end
  // up with both chart and table hidden.
  function toggleChart() {
    setShowChart((current) => (current && !showTable ? current : !current))
  }

  function toggleTable() {
    setShowTable((current) => (current && !showChart ? current : !current))
  }

  function toggleMpan(mpan: string) {
    setSelectedMpans((current) => {
      const next = new Set(current)
      if (next.has(mpan)) next.delete(mpan)
      else next.add(mpan)
      return next
    })
  }

  const hasAnyUsage =
    rows?.some((row) => Object.values(row.byMpan).some((f) => f.kwh !== 0 || f.total !== 0)) ?? false

  return (
    <section className="usage-by-day">
      <div className="usage-by-day__heading-row">
        <h1>Usage by day</h1>
        <div className="usage-by-day__view-toggles">
          <button
            type="button"
            className={showChart ? 'active' : ''}
            onClick={toggleChart}
            disabled={showChart && !showTable}
            aria-pressed={showChart}
            aria-label="Show chart"
            title="Show chart"
          >
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true">
              <rect x="2" y="10" width="4" height="8" fill="currentColor" />
              <rect x="8" y="5" width="4" height="13" fill="currentColor" />
              <rect x="14" y="2" width="4" height="16" fill="currentColor" />
            </svg>
          </button>
          <button
            type="button"
            className={showTable ? 'active' : ''}
            onClick={toggleTable}
            disabled={showTable && !showChart}
            aria-pressed={showTable}
            aria-label="Show table"
            title="Show table"
          >
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true">
              <rect x="2" y="2" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.5" />
              <line x1="2" y1="7.33" x2="18" y2="7.33" stroke="currentColor" strokeWidth="1.5" />
              <line x1="2" y1="12.67" x2="18" y2="12.67" stroke="currentColor" strokeWidth="1.5" />
              <line x1="8.67" y1="2" x2="8.67" y2="18" stroke="currentColor" strokeWidth="1.5" />
            </svg>
          </button>
        </div>
      </div>

      {meterPoints && meterPoints.length > 0 && (
        <div className="usage-by-day__mpan-toggles">
          {meterPoints.map((mp) => (
            <label key={mp.mpan} className="usage-by-day__mpan-toggle">
              <input
                type="checkbox"
                checked={selectedMpans?.has(mp.mpan) ?? true}
                onChange={() => toggleMpan(mp.mpan)}
              />
              MPAN {mp.mpan} &ndash; {meterPointLabel(mp)}
            </label>
          ))}
        </div>
      )}

      <div className="usage-by-day__controls">
        <label>
          Month
          <select value={month} onChange={(e) => setMonth(Number(e.target.value))}>
            {MONTH_NAMES.map((name, index) => (
              <option key={name} value={index + 1}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Year
          <select value={year} onChange={(e) => setYear(Number(e.target.value))}>
            {yearOptions.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        </label>
        <div className="usage-by-day__month-nav">
          <button type="button" onClick={goToPreviousMonth} aria-label="Previous month">
            &larr;
          </button>
          <button type="button" onClick={goToNextMonth} aria-label="Next month">
            &rarr;
          </button>
        </div>
      </div>

      {error && <p className="usage-by-day__error">{error}</p>}
      {!error && !rows && <p>Loading usage data…</p>}
      {!error && rows && !hasAnyUsage && (
        <p>
          No usage data for {MONTH_NAMES[month - 1]} {year}.
        </p>
      )}

      {!error && rows && hasAnyUsage && showChart && (
        <div className="usage-by-day__chart-section">
          <div className="usage-by-day__metric-toggle">
            <button
              type="button"
              className={chartMetric === 'kwh' ? 'active' : ''}
              onClick={() => setChartMetric('kwh')}
            >
              Usage (kWh)
            </button>
            <button
              type="button"
              className={chartMetric === 'cost' ? 'active' : ''}
              onClick={() => setChartMetric('cost')}
            >
              Cost (£)
            </button>
          </div>
          <UsageBarChart rows={rows} meterPoints={includedMeterPoints} metric={chartMetric} />
        </div>
      )}

      {!error && rows && hasAnyUsage && showTable && (
        <div className="usage-by-day__table-wrap">
          <table className="usage-by-day__table">
            <thead>
              <tr>
                <th className="usage-by-day__label-col" rowSpan={2}>
                  Day
                </th>
                <th className="usage-by-day__label-col" rowSpan={2}>
                  Date
                </th>
                {includedMeterPoints.map((mp) => (
                  <th
                    key={mp.mpan}
                    className="usage-by-day__group-start usage-by-day__mpan-header"
                    colSpan={offPeakAvailableByMpan?.get(mp.mpan) ? 6 : 5}
                  >
                    MPAN {mp.mpan} - {meterPointLabel(mp)}
                  </th>
                ))}
                <th className="usage-by-day__group-start" colSpan={2}>
                  TOTAL
                </th>
              </tr>
              <tr>
                {includedMeterPoints.map((mp) => (
                  <Fragment key={mp.mpan}>
                    <th className="usage-by-day__group-start">
                      Usage
                      <br />
                      (kWh)
                    </th>
                    {offPeakAvailableByMpan?.get(mp.mpan) && (
                      <th>
                        Off Peak
                        <br />
                        (%)
                      </th>
                    )}
                    <th>
                      Avg Rate
                      <br />
                      (£/kWh)
                    </th>
                    <th>
                      Usage Cost
                      <br />
                      (£)
                    </th>
                    <th>
                      Std Chg
                      <br />
                      (£)
                    </th>
                    <th>
                      Total
                      <br />
                      (£)
                    </th>
                  </Fragment>
                ))}
                <th className="usage-by-day__group-start">
                  Usage
                  <br />
                  (kWh)
                </th>
                <th>
                  Total
                  <br />
                  (£)
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const total = grandTotal(row.byMpan)
                return (
                  <tr key={row.date}>
                    <td className="usage-by-day__label-col">{dayOfWeek(row.date)}</td>
                    <td className="usage-by-day__label-col">{formatDate(row.date)}</td>
                    {includedMeterPoints.map((mp) => {
                      const f = row.byMpan[mp.mpan] ?? emptyFigures()
                      return (
                        <Fragment key={mp.mpan}>
                          <td className="usage-by-day__group-start">{formatKwh(f.kwh)}</td>
                          {offPeakAvailableByMpan?.get(mp.mpan) && <td>{formatPercent(offPeakPct(f))}</td>}
                          <td>{formatRate(f.avgRate)}</td>
                          <td>{formatCost(f.usageCost)}</td>
                          <td>{formatCost(f.stdChg)}</td>
                          <td>{formatCost(f.total)}</td>
                        </Fragment>
                      )
                    })}
                    <td className="usage-by-day__group-start">{formatKwh(total.kwh)}</td>
                    <td>{formatCost(total.total)}</td>
                  </tr>
                )
              })}
            </tbody>
            {totalsByMpan && (
              <tfoot>
                <tr className="usage-by-day__total-row">
                  <td className="usage-by-day__label-col" colSpan={2}>
                    TOTAL
                  </td>
                  {includedMeterPoints.map((mp) => {
                    const f = totalsByMpan.get(mp.mpan) ?? emptyFigures()
                    return (
                      <Fragment key={mp.mpan}>
                        <td className="usage-by-day__group-start">{formatKwh(f.kwh)}</td>
                        {offPeakAvailableByMpan?.get(mp.mpan) && <td>{formatPercent(offPeakPct(f))}</td>}
                        <td>{formatRate(f.avgRate)}</td>
                        <td>{formatCost(f.usageCost)}</td>
                        <td>{formatCost(f.stdChg)}</td>
                        <td>{formatCost(f.total)}</td>
                      </Fragment>
                    )
                  })}
                  {(() => {
                    const byMpan: Record<string, MpanFigures> = {}
                    for (const mp of includedMeterPoints) byMpan[mp.mpan] = totalsByMpan.get(mp.mpan) ?? emptyFigures()
                    const total = grandTotal(byMpan)
                    return (
                      <>
                        <td className="usage-by-day__group-start">{formatKwh(total.kwh)}</td>
                        <td>{formatCost(total.total)}</td>
                      </>
                    )
                  })()}
                </tr>
                <tr className="usage-by-day__average-row">
                  <td className="usage-by-day__label-col" colSpan={2}>
                    AVG / DAY
                  </td>
                  {includedMeterPoints.map((mp) => {
                    const f = averageFigures(totalsByMpan.get(mp.mpan) ?? emptyFigures(), daysWithData(mp.mpan))
                    return (
                      <Fragment key={mp.mpan}>
                        <td className="usage-by-day__group-start">{formatKwh(f.kwh)}</td>
                        {offPeakAvailableByMpan?.get(mp.mpan) && <td>{formatPercent(offPeakPct(f))}</td>}
                        <td>{formatRate(f.avgRate)}</td>
                        <td>{formatCost(f.usageCost)}</td>
                        <td>{formatCost(f.stdChg)}</td>
                        <td>{formatCost(f.total)}</td>
                      </Fragment>
                    )
                  })}
                  {(() => {
                    // Each MPAN can have its own days-with-data, so average per MPAN first,
                    // then sum those averages, rather than summing totals over one shared divisor.
                    let kwh = 0
                    let total = 0
                    for (const mp of includedMeterPoints) {
                      const f = averageFigures(totalsByMpan.get(mp.mpan) ?? emptyFigures(), daysWithData(mp.mpan))
                      kwh += f.kwh
                      total += f.total
                    }
                    return (
                      <>
                        <td className="usage-by-day__group-start">{formatKwh(kwh)}</td>
                        <td>{formatCost(total)}</td>
                      </>
                    )
                  })()}
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </section>
  )
}
