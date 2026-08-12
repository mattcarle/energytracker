import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { MeterPoint } from '../api/types'
import NetUsageBarChart from '../components/NetUsageBarChart'
import UsageBarChart from '../components/UsageBarChart'
import {
  CHART_VIEWS,
  addFigures,
  averageFigures,
  emptyFigures,
  formatCost,
  formatKwh,
  formatPercent,
  formatRate,
  isKwhView,
  isNetView,
  meterPointLabel,
  offPeakPct,
  type ChartView,
  type MpanFigures,
  type PeriodRow,
} from './usageShared'
import './UsagePage.css'

export interface PeriodColumn {
  header: ReactNode
  render: (row: PeriodRow) => ReactNode
}

interface UsagePeriodViewProps {
  title: string
  periodColumns: PeriodColumn[]
  averageRowLabel: string
  controls?: ReactNode
  rows: PeriodRow[] | null
  meterPoints: MeterPoint[] | null
  offPeakAvailableByMpan: Map<string, boolean> | null
  latestPeriodKeyByMpan: Map<string, string | null> | null
  error: string | null
  noDataMessage: ReactNode
}

// Shared table/chart rendering for every usage-by-X page - each page supplies its own period
// selector (via `controls`) and how a row's period key(s) are rendered/labelled (via
// `periodColumns`), and this owns the MPAN filter, chart/table visibility toggles, chart metric
// selection, and the totals/average footer rows, identically across granularities.
export default function UsagePeriodView({
  title,
  periodColumns,
  averageRowLabel,
  controls,
  rows,
  meterPoints,
  offPeakAvailableByMpan,
  latestPeriodKeyByMpan,
  error,
  noDataMessage,
}: UsagePeriodViewProps) {
  const [selectedMpans, setSelectedMpans] = useState<Set<string> | null>(null)
  const [showChart, setShowChart] = useState(true)
  const [showTable, setShowTable] = useState(true)
  const [chartView, setChartView] = useState<ChartView>('usage')

  useEffect(() => {
    if (!meterPoints) return
    setSelectedMpans((current) => current ?? new Set(meterPoints.filter((p) => p.meterType !== 'GAS').map((p) => p.mpan)))
  }, [meterPoints])

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

  // Same figure as the table's TOTAL row/column - "cost" here means usage cost + standing
  // charge, matching what the chart's stacked "Cost (£)" bars actually add up to.
  const chartTotal = useMemo(() => {
    if (!totalsByMpan) return null
    let kwh = 0
    let total = 0
    for (const mp of includedMeterPoints) {
      const figures = totalsByMpan.get(mp.mpan) ?? emptyFigures()
      kwh += figures.kwh
      total += figures.total
    }
    return { kwh, total }
  }, [totalsByMpan, includedMeterPoints])

  // Matches the standing-charge cutoff in useUsagePeriodData: for a part-way-through-the-range
  // view, average over the periods we actually have usage data for rather than every expected
  // period. A fully-elapsed range naturally has data through its last period, so this has no
  // effect there.
  function periodsWithData(mpan: string): number {
    const latest = latestPeriodKeyByMpan?.get(mpan)
    if (!latest || !rows) return 0
    return rows.filter((row) => row.key <= latest).length
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

  const labelColCount = periodColumns.length

  return (
    <section className="usage-page">
      <div className="usage-page__heading-row">
        <h1>{title}</h1>
        <div className="usage-page__view-toggles">
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
        <div className="usage-page__mpan-toggles">
          {meterPoints.map((mp) => (
            <label key={mp.mpan} className="usage-page__mpan-toggle">
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

      {controls && <div className="usage-page__controls">{controls}</div>}

      {error && <p className="usage-page__error">{error}</p>}
      {!error && !rows && <p>Loading usage data…</p>}
      {!error && rows && !hasAnyUsage && <p>{noDataMessage}</p>}

      {!error && rows && hasAnyUsage && showChart && (
        <div className="usage-page__chart-section">
          <div className="usage-page__chart-toolbar">
            <div className="usage-page__metric-toggle">
              {CHART_VIEWS.map(({ view, label }) => (
                <button
                  key={view}
                  type="button"
                  className={chartView === view ? 'active' : ''}
                  onClick={() => setChartView(view)}
                >
                  {label}
                </button>
              ))}
            </div>
            {chartTotal && (
              <div className="usage-page__chart-total">
                Total: {isKwhView(chartView) ? `${formatKwh(chartTotal.kwh)} kWh` : `£${formatCost(chartTotal.total)}`}
              </div>
            )}
          </div>
          {isNetView(chartView) ? (
            <NetUsageBarChart
              rows={rows}
              meterPoints={includedMeterPoints}
              metric={isKwhView(chartView) ? 'kwh' : 'cost'}
            />
          ) : (
            <UsageBarChart
              rows={rows}
              meterPoints={includedMeterPoints}
              metric={isKwhView(chartView) ? 'kwh' : 'cost'}
              offPeakAvailableByMpan={offPeakAvailableByMpan ?? undefined}
            />
          )}
        </div>
      )}

      {!error && rows && hasAnyUsage && showTable && (
        <div className="usage-page__table-wrap">
          <table className="usage-page__table">
            <thead>
              <tr>
                {periodColumns.map((col, index) => (
                  <th key={index} className="usage-page__label-col" rowSpan={2}>
                    {col.header}
                  </th>
                ))}
                {includedMeterPoints.map((mp) => (
                  <th
                    key={mp.mpan}
                    className="usage-page__group-start usage-page__mpan-header"
                    colSpan={offPeakAvailableByMpan?.get(mp.mpan) ? 6 : 5}
                  >
                    MPAN {mp.mpan} - {meterPointLabel(mp)}
                  </th>
                ))}
                <th className="usage-page__group-start" colSpan={2}>
                  TOTAL
                </th>
              </tr>
              <tr>
                {includedMeterPoints.map((mp) => (
                  <Fragment key={mp.mpan}>
                    <th className="usage-page__group-start">
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
                <th className="usage-page__group-start">
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
                  <tr key={row.key}>
                    {periodColumns.map((col, index) => (
                      <td key={index} className="usage-page__label-col">
                        {col.render(row)}
                      </td>
                    ))}
                    {includedMeterPoints.map((mp) => {
                      const f = row.byMpan[mp.mpan] ?? emptyFigures()
                      return (
                        <Fragment key={mp.mpan}>
                          <td className="usage-page__group-start">{formatKwh(f.kwh)}</td>
                          {offPeakAvailableByMpan?.get(mp.mpan) && <td>{formatPercent(offPeakPct(f))}</td>}
                          <td>{formatRate(f.avgRate)}</td>
                          <td>{formatCost(f.usageCost)}</td>
                          <td>{formatCost(f.stdChg)}</td>
                          <td>{formatCost(f.total)}</td>
                        </Fragment>
                      )
                    })}
                    <td className="usage-page__group-start">{formatKwh(total.kwh)}</td>
                    <td>{formatCost(total.total)}</td>
                  </tr>
                )
              })}
            </tbody>
            {totalsByMpan && (
              <tfoot>
                <tr className="usage-page__total-row">
                  <td className="usage-page__label-col" colSpan={labelColCount}>
                    TOTAL
                  </td>
                  {includedMeterPoints.map((mp) => {
                    const f = totalsByMpan.get(mp.mpan) ?? emptyFigures()
                    return (
                      <Fragment key={mp.mpan}>
                        <td className="usage-page__group-start">{formatKwh(f.kwh)}</td>
                        {/* Off Peak (%) and Avg Rate are ratios, not summable totals - the AVG
                            row below already shows them, so leave these blank rather than repeat. */}
                        {offPeakAvailableByMpan?.get(mp.mpan) && <td>&nbsp;</td>}
                        <td>&nbsp;</td>
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
                        <td className="usage-page__group-start">{formatKwh(total.kwh)}</td>
                        <td>{formatCost(total.total)}</td>
                      </>
                    )
                  })()}
                </tr>
                <tr className="usage-page__average-row">
                  <td className="usage-page__label-col" colSpan={labelColCount}>
                    {averageRowLabel}
                  </td>
                  {includedMeterPoints.map((mp) => {
                    const f = averageFigures(totalsByMpan.get(mp.mpan) ?? emptyFigures(), periodsWithData(mp.mpan))
                    return (
                      <Fragment key={mp.mpan}>
                        <td className="usage-page__group-start">{formatKwh(f.kwh)}</td>
                        {offPeakAvailableByMpan?.get(mp.mpan) && <td>{formatPercent(offPeakPct(f))}</td>}
                        <td>{formatRate(f.avgRate)}</td>
                        <td>{formatCost(f.usageCost)}</td>
                        <td>{formatCost(f.stdChg)}</td>
                        <td>{formatCost(f.total)}</td>
                      </Fragment>
                    )
                  })}
                  {(() => {
                    // Each MPAN can have its own periods-with-data, so average per MPAN first,
                    // then sum those averages, rather than summing totals over one shared divisor.
                    let kwh = 0
                    let total = 0
                    for (const mp of includedMeterPoints) {
                      const f = averageFigures(totalsByMpan.get(mp.mpan) ?? emptyFigures(), periodsWithData(mp.mpan))
                      kwh += f.kwh
                      total += f.total
                    }
                    return (
                      <>
                        <td className="usage-page__group-start">{formatKwh(kwh)}</td>
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
