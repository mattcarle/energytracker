import { Fragment, useEffect, useMemo, useState } from 'react'
import { getMeterPoints, getStandingChargesByDay, getUsageByDay } from '../api/client'
import type { MeterPoint } from '../api/types'
import './UsageByDay.css'

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => CURRENT_YEAR - i)

interface MpanFigures {
  kwh: number
  avgRate: number | null
  usageCost: number
  stdChg: number
  total: number
}

interface DayRow {
  date: string
  byMpan: Record<string, MpanFigures>
}

function emptyFigures(): MpanFigures {
  return { kwh: 0, avgRate: null, usageCost: 0, stdChg: 0, total: 0 }
}

function addFigures(a: MpanFigures, b: MpanFigures): MpanFigures {
  const kwh = a.kwh + b.kwh
  const usageCost = a.usageCost + b.usageCost
  const stdChg = a.stdChg + b.stdChg
  return { kwh, usageCost, stdChg, total: usageCost + stdChg, avgRate: kwh !== 0 ? usageCost / kwh : null }
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

function meterPointLabel(meterPoint: MeterPoint): string {
  if (meterPoint.meterType === 'GAS') return 'Gas'
  return meterPoint.isExport ? 'Electricity (Export)' : 'Electricity (Import)'
}

function formatDate(dateStr: string): string {
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

export default function UsageByDay() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [meterPoints, setMeterPoints] = useState<MeterPoint[] | null>(null)
  const [selectedMpans, setSelectedMpans] = useState<Set<string> | null>(null)
  const [rows, setRows] = useState<DayRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMeterPoints()
      .then((points) => {
        if (cancelled) return
        setMeterPoints(points)
        setSelectedMpans((current) => current ?? new Set(points.map((p) => p.mpan)))
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

        for (const { mpan, usageDays, standingCharges } of perMpanResults) {
          const stdChgByDate = new Map(standingCharges.map((s) => [s.chargeDate, s.amount]))

          for (const day of usageDays) {
            const row = rowByDate.get(day.usageDate)
            if (!row) continue
            const stdChg = stdChgByDate.get(day.usageDate) ?? 0
            row.byMpan[mpan] = {
              kwh: day.kwh,
              avgRate: day.avgRate,
              usageCost: day.cost,
              stdChg,
              total: day.cost + stdChg,
            }
          }

          // A standing charge still applies on days with zero consumption, so make sure
          // those days aren't left without a figure just because there was no usage row.
          for (const [date, amount] of stdChgByDate) {
            const row = rowByDate.get(date)
            if (!row || row.byMpan[mpan]) continue
            row.byMpan[mpan] = { kwh: 0, avgRate: null, usageCost: 0, stdChg: amount, total: amount }
          }
        }

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
      <h1>Usage by day</h1>

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
            {YEAR_OPTIONS.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        </label>
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

      {error && <p className="usage-by-day__error">{error}</p>}
      {!error && !rows && <p>Loading usage data…</p>}
      {!error && rows && !hasAnyUsage && (
        <p>
          No usage data for {MONTH_NAMES[month - 1]} {year}.
        </p>
      )}

      {!error && rows && hasAnyUsage && (
        <div className="usage-by-day__table-wrap">
          <table className="usage-by-day__table">
            <thead>
              <tr>
                <th className="usage-by-day__date-col" rowSpan={3}>
                  Date
                </th>
                {includedMeterPoints.map((mp) => (
                  <th key={mp.mpan} className="usage-by-day__group-start" colSpan={5}>
                    MPAN {mp.mpan}
                  </th>
                ))}
                <th className="usage-by-day__group-start" colSpan={2}>
                  TOTAL
                </th>
              </tr>
              <tr>
                {includedMeterPoints.map((mp) => (
                  <th key={mp.mpan} className="usage-by-day__group-start" colSpan={5}>
                    {meterPointLabel(mp)}
                  </th>
                ))}
                <th className="usage-by-day__group-start" colSpan={2} />
              </tr>
              <tr>
                {includedMeterPoints.map((mp) => (
                  <Fragment key={mp.mpan}>
                    <th className="usage-by-day__group-start">Usage (kWh)</th>
                    <th>Avg Rate (£/kWh)</th>
                    <th>Usage Cost (£)</th>
                    <th>Std Chg (£)</th>
                    <th>Total (£)</th>
                  </Fragment>
                ))}
                <th className="usage-by-day__group-start">Usage (kWh)</th>
                <th>Total (£)</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const total = grandTotal(row.byMpan)
                return (
                  <tr key={row.date}>
                    <td className="usage-by-day__date-col">{formatDate(row.date)}</td>
                    {includedMeterPoints.map((mp) => {
                      const f = row.byMpan[mp.mpan] ?? emptyFigures()
                      return (
                        <Fragment key={mp.mpan}>
                          <td className="usage-by-day__group-start">{formatKwh(f.kwh)}</td>
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
                  <td className="usage-by-day__date-col">TOTAL</td>
                  {includedMeterPoints.map((mp) => {
                    const f = totalsByMpan.get(mp.mpan) ?? emptyFigures()
                    return (
                      <Fragment key={mp.mpan}>
                        <td className="usage-by-day__group-start">{formatKwh(f.kwh)}</td>
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
              </tfoot>
            )}
          </table>
        </div>
      )}
    </section>
  )
}
