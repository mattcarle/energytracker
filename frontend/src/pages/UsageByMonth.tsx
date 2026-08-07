import { useEffect, useMemo, useState } from 'react'
import { getMeterPoints, getUsageByDay } from '../api/client'
import './UsageByMonth.css'

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => CURRENT_YEAR - i)

interface DayTotals {
  gasKwh: number
  gasCost: number
  importKwh: number
  importCost: number
  exportKwh: number
  exportCost: number
}

interface DayRow extends DayTotals {
  date: string
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

function emptyDayTotals(): DayTotals {
  return { gasKwh: 0, gasCost: 0, importKwh: 0, importCost: 0, exportKwh: 0, exportCost: 0 }
}

function addDayTotals(a: DayTotals, b: DayTotals): DayTotals {
  return {
    gasKwh: a.gasKwh + b.gasKwh,
    gasCost: a.gasCost + b.gasCost,
    importKwh: a.importKwh + b.importKwh,
    importCost: a.importCost + b.importCost,
    exportKwh: a.exportKwh + b.exportKwh,
    exportCost: a.exportCost + b.exportCost,
  }
}

function deriveColumns(t: DayTotals) {
  const elecNetKwh = t.importKwh + t.exportKwh
  const elecNetCost = t.importCost + t.exportCost
  const totalNetKwh = elecNetKwh + t.gasKwh
  const totalNetCost = elecNetCost + t.gasCost
  const avgImportRate = t.importKwh !== 0 ? t.importCost / t.importKwh : null
  return { elecNetKwh, elecNetCost, totalNetKwh, totalNetCost, avgImportRate }
}

function formatDate(dateStr: string): string {
  const [, monthPart, dayPart] = dateStr.split('-')
  const monthIndex = Number(monthPart) - 1
  return `${Number(dayPart)} ${MONTH_NAMES[monthIndex].slice(0, 3)}`
}

function formatKwh(value: number): string {
  return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatCost(value: number): string {
  const formatted = Math.abs(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return value < 0 ? `-£${formatted}` : `£${formatted}`
}

function formatRate(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '–'
  const pence = value * 100
  return `${pence.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}p/kWh`
}

export default function UsageByMonth() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [rows, setRows] = useState<DayRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setError(null)

    const fromDate = firstOfMonth(year, month)
    const toDate = firstOfNextMonth(year, month)
    const totalDays = daysInMonth(year, month)

    getMeterPoints()
      .then((meterPoints) =>
        Promise.all(
          meterPoints.map((meterPoint) =>
            getUsageByDay(meterPoint.mpan, fromDate, toDate).then((response) => ({
              meterPoint,
              days: response.days,
            })),
          ),
        ),
      )
      .then((results) => {
        if (cancelled) return

        const byDate = new Map<string, DayTotals>()
        for (let day = 1; day <= totalDays; day++) {
          byDate.set(`${year}-${pad2(month)}-${pad2(day)}`, emptyDayTotals())
        }

        for (const { meterPoint, days } of results) {
          for (const usage of days) {
            const existing = byDate.get(usage.usageDate)
            if (!existing) continue

            if (meterPoint.meterType === 'GAS') {
              existing.gasKwh += usage.kwh
              existing.gasCost += usage.cost
            } else if (meterPoint.isExport) {
              existing.exportKwh += -usage.kwh
              existing.exportCost += -usage.cost
            } else {
              existing.importKwh += usage.kwh
              existing.importCost += usage.cost
            }
          }
        }

        setRows(
          Array.from(byDate.entries())
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([date, totals]) => ({ date, ...totals })),
        )
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load usage data')
      })

    return () => {
      cancelled = true
    }
  }, [year, month])

  const total = useMemo(() => (rows ? rows.reduce(addDayTotals, emptyDayTotals()) : null), [rows])

  const hasAnyUsage =
    rows?.some((row) => row.gasKwh !== 0 || row.importKwh !== 0 || row.exportKwh !== 0) ?? false

  return (
    <section className="usage-by-month">
      <h1>Usage by day</h1>

      <div className="usage-by-month__controls">
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

      {error && <p className="usage-by-month__error">{error}</p>}
      {!error && !rows && <p>Loading usage data…</p>}
      {!error && rows && !hasAnyUsage && (
        <p>
          No usage data for {MONTH_NAMES[month - 1]} {year}.
        </p>
      )}

      {!error && rows && hasAnyUsage && (
        <div className="usage-by-month__table-wrap">
          <table className="usage-by-month__table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Gas (kWh)</th>
                <th>Gas Cost</th>
                <th>Elec Import (kWh)</th>
                <th>Avg Import</th>
                <th>Elec Export Cost</th>
                <th>Elec Net (kWh)</th>
                <th>Elec Net Cost</th>
                <th>Total Net (kWh)</th>
                <th>Total Net Cost</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const d = deriveColumns(row)
                return (
                  <tr key={row.date}>
                    <td>{formatDate(row.date)}</td>
                    <td>{formatKwh(row.gasKwh)}</td>
                    <td>{formatCost(row.gasCost)}</td>
                    <td>{formatKwh(row.importKwh)}</td>
                    <td>{formatRate(d.avgImportRate)}</td>
                    <td>{formatCost(row.exportCost)}</td>
                    <td>{formatKwh(d.elecNetKwh)}</td>
                    <td>{formatCost(d.elecNetCost)}</td>
                    <td>{formatKwh(d.totalNetKwh)}</td>
                    <td>{formatCost(d.totalNetCost)}</td>
                  </tr>
                )
              })}
            </tbody>
            {total && (
              <tfoot>
                {(() => {
                  const d = deriveColumns(total)
                  return (
                    <tr className="usage-by-month__net-row usage-by-month__net-row--total">
                      <td>Total</td>
                      <td>{formatKwh(total.gasKwh)}</td>
                      <td>{formatCost(total.gasCost)}</td>
                      <td>{formatKwh(total.importKwh)}</td>
                      <td>{formatRate(d.avgImportRate)}</td>
                      <td>{formatCost(total.exportCost)}</td>
                      <td>{formatKwh(d.elecNetKwh)}</td>
                      <td>{formatCost(d.elecNetCost)}</td>
                      <td>{formatKwh(d.totalNetKwh)}</td>
                      <td>{formatCost(d.totalNetCost)}</td>
                    </tr>
                  )
                })()}
              </tfoot>
            )}
          </table>
        </div>
      )}
    </section>
  )
}
