import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { MeterPoint } from '../api/types'
import NetUsageBarChart from '../components/NetUsageBarChart'
import UsageBarChart from '../components/UsageBarChart'
import { useIsMobile } from '../hooks/useIsMobile'
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
  stdChgDaysByMpan: Map<string, Set<string>> | null
  error: string | null
  noDataMessage: ReactNode
  // Solar overlay - all four optional/default-off so a page can omit them entirely if solar
  // isn't relevant. solarByKey holds kWh for the period-based pages or a Watts power curve for
  // the Day page (see UsageBarChart's SolarOverlayProps) - solarTotalKwh is always kWh
  // regardless, since it drives the toolbar/insights figures rather than the chart line itself.
  solarByKey?: Map<string, number> | null
  solarTotalKwh?: number | null
  solarAvailable?: boolean
  solarUnit?: 'kWh' | 'W'
  // Opt-in, so a future usage-by-X page can still fall back to chart/table-only behaviour.
  enableInsights?: boolean
  // Singular period noun for the Insights section's "Average per X" cards - "Day" for Usage by
  // day, "Week" for Usage by week, and so on. Only meaningful (and required in practice) when
  // enableInsights is set.
  insightsPeriodLabel?: string
  // The Insights section's "Summary for X" heading - e.g. "Aug 2026" for Usage by day, "2026"
  // for Usage by week/month. Left undefined where there's no single period to name (Usage by
  // year spans every year with data), in which case the heading just reads "Summary".
  periodSummaryLabel?: string
}

// Flags a Usage (kWh) table cell whose figure includes at least one data-integrity-check
// placeholder interval (see MpanFigures.missingIntervalCount) by rendering it in red, explained
// by the legend below the table - so a period reading 0 kWh because nothing was recorded reads
// differently from one that's genuinely zero usage. The title attribute (a native tooltip, not
// a custom-built one - nothing else in this app needs richer hover UI) covers the whole figure.
function KwhCell({ kwh, intervalCount, missingIntervalCount }: { kwh: number; intervalCount: number; missingIntervalCount: number }): ReactNode {
  if (missingIntervalCount <= 0) return formatKwh(kwh)
  return (
    <span
      className="usage-page__missing-cell"
      title={`Data missing for ${missingIntervalCount} out of ${intervalCount} periods`}
    >
      {formatKwh(kwh)}
    </span>
  )
}

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="usage-page__stat-tile">
      <div className="usage-page__stat-label">{label}</div>
      <div className="usage-page__stat-value">{value}</div>
    </div>
  )
}

// formatCost puts the minus sign in front of the digits ("-28.42"), not the £ - move it in
// front of the £ instead so a negative figure reads "-£28.42" rather than "£-28.42".
function formatPoundValue(cost: number): string {
  const formatted = formatCost(cost)
  return formatted.startsWith('-') ? `-£${formatted.slice(1)}` : `£${formatted}`
}

function formatRatePence(kwh: number, cost: number): string {
  const ratePence = kwh !== 0 ? (cost / kwh) * 100 : null
  return ratePence !== null && Number.isFinite(ratePence)
    ? `${ratePence.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}p`
    : '–'
}

// Shows the calculation behind a figure rather than just the total, e.g.
// "158.42 kWh @ 3.99p = £6.33" - rate is derived from cost/kWh (in pence) rather than read off
// MpanFigures.avgRate, since that's kept in £ at 4dp for the table/chart, not p at 2dp.
function formatKwhCostLine(kwh: number, cost: number): string {
  return `${formatKwh(kwh)} kWh @ ${formatRatePence(kwh, cost)} = ${formatPoundValue(cost)}`
}

// Same idea as formatKwhCostLine but for a standing charge, which accrues per calendar day
// regardless of the page's own row granularity - always reported in days (e.g. "31 days @
// 45.00p = £13.80"), even on a week/month/year/half-hour page whose rows aren't one-per-day.
function formatStdChargeLine(dayCount: number, stdChg: number): string {
  const ratePence = dayCount > 0 ? (stdChg / dayCount) * 100 : null
  const rateStr =
    ratePence !== null && Number.isFinite(ratePence)
      ? `${ratePence.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}p`
      : '–'
  return `${dayCount} day${dayCount === 1 ? '' : 's'} @ ${rateStr} = ${formatPoundValue(stdChg)}`
}

// Combined "kWh = cost" card, without a rate - used for the Total cards, where the rate (if
// relevant) already has its own dedicated card.
function formatKwhAndCostLine(kwh: number, cost: number): string {
  return `${formatKwh(kwh)} kWh | ${formatPoundValue(cost)}`
}

// Same "kWh | cost" format as the Total card, but averaged over the periods that Total is a sum
// of - periodCount is 0 whenever there's no usage data yet to average over (e.g. nothing selected).
function formatAveragePerPeriodLine(kwh: number, cost: number, periodCount: number): string {
  if (periodCount <= 0) return '–'
  return formatKwhAndCostLine(kwh / periodCount, cost / periodCount)
}

// Solar has no cost/rate concept in this feature - just the kWh half of formatKwhAndCostLine.
function formatKwhOnlyLine(kwh: number): string {
  return `${formatKwh(kwh)} kWh`
}

function formatSolarAveragePerPeriodLine(totalKwh: number, periodCount: number): string {
  if (periodCount <= 0) return '–'
  return formatKwhOnlyLine(totalKwh / periodCount)
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
  stdChgDaysByMpan,
  error,
  noDataMessage,
  enableInsights = false,
  insightsPeriodLabel = 'Day',
  periodSummaryLabel,
  solarByKey = null,
  solarTotalKwh = null,
  solarAvailable = false,
  solarUnit = 'kWh',
}: UsagePeriodViewProps) {
  const isMobile = useIsMobile()
  const [selectedMpans, setSelectedMpans] = useState<Set<string> | null>(null)
  const [showChart, setShowChart] = useState(true)
  const [showTable, setShowTable] = useState(true)
  const [showInsights, setShowInsights] = useState(true)
  const [chartView, setChartView] = useState<ChartView>('usage')
  const [showSolar, setShowSolar] = useState(true)

  // The table isn't offered on mobile at all - deriving this rather than forcing showTable
  // itself to false keeps the desktop toggle state intact if the viewport is later resized back
  // up past the mobile breakpoint.
  const tableVisible = !isMobile && showTable

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

  // missingIntervalCount propagates upward through addFigures, so any MPAN's all-rows total
  // already reflects whether any row/period contributed a placeholder interval - checking just
  // the totals here is equivalent to (and cheaper than) scanning every row.
  const tableHasMissing = totalsByMpan
    ? Array.from(totalsByMpan.values()).some((f) => f.missingIntervalCount > 0)
    : false

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

  // Whether solar should actually be shown right now - available (Growatt configured) and the
  // checkbox is on. Gates the chart overlay, the toolbar total, and the insights section
  // identically, the same way selectedMpans gates the MPAN-driven equivalents of each.
  const solarActive = solarAvailable && showSolar && solarByKey !== null && solarTotalKwh !== null

  // Count of rows solarByKey actually has a value for, not rows.length - a page showing a
  // partly-future range (e.g. the rest of this month) shouldn't dilute the average with periods
  // that haven't happened yet. The backend never returns a row for a period with no data, so
  // "has an entry" is already exactly the right cutoff, without needing a separate "latest key"
  // concept the way the MPAN-driven groups above do.
  const solarPeriodCount = useMemo(() => {
    if (!solarByKey || !rows) return 0
    return rows.filter((row) => solarByKey.has(row.key)).length
  }, [solarByKey, rows])

  // Matches the standing-charge cutoff in useUsagePeriodData: for a part-way-through-the-range
  // view, average over the periods we actually have usage data for rather than every expected
  // period. A fully-elapsed range naturally has data through its last period, so this has no
  // effect there.
  function periodsWithData(mpan: string): number {
    const latest = latestPeriodKeyByMpan?.get(mpan)
    if (!latest || !rows) return 0
    return rows.filter((row) => row.key <= latest).length
  }

  function grandTotal(
    byMpan: Record<string, MpanFigures>,
  ): { kwh: number; total: number; intervalCount: number; missingIntervalCount: number } {
    let kwh = 0
    let total = 0
    let intervalCount = 0
    let missingIntervalCount = 0
    for (const mp of includedMeterPoints) {
      const figures = byMpan[mp.mpan] ?? emptyFigures()
      kwh += figures.kwh
      total += figures.total
      intervalCount += figures.intervalCount
      missingIntervalCount += figures.missingIntervalCount
    }
    return { kwh, total, intervalCount, missingIntervalCount }
  }

  // Groups the currently-selected MPANs (the same checkbox filter the chart/table use) into
  // electricity import/export and gas, and sums each group's period totals - a section is
  // simply omitted below when its group is empty, i.e. no MPAN of that kind is selected.
  const insightsData = useMemo(() => {
    if (!enableInsights) return null

    function sumFigures(mpans: MeterPoint[]): MpanFigures {
      return mpans.reduce((acc, mp) => addFigures(acc, totalsByMpan?.get(mp.mpan) ?? emptyFigures()), emptyFigures())
    }

    // Number of distinct calendar days any of the group's MPANs was actually charged a standing
    // charge on - the divisor for that group's blended daily standing-charge rate. Unioned
    // across the group's MPANs (via the Set) rather than summed per-MPAN, so two MPANs charged
    // on the same day count as one day, not two.
    function stdChgDayCount(mpans: MeterPoint[]): number {
      const days = new Set<string>()
      for (const mp of mpans) {
        for (const day of stdChgDaysByMpan?.get(mp.mpan) ?? []) days.add(day)
      }
      return days.size
    }

    // Same cutoff as periodsWithData above, generalised to a group of MPANs: how many periods
    // (of the range currently shown) actually have usage data for any MPAN in the group - the
    // divisor for "Average per {insightsPeriodLabel}".
    function usagePeriodCount(mpans: MeterPoint[]): number {
      if (!rows) return 0
      let latestKey: string | null = null
      for (const mp of mpans) {
        const key = latestPeriodKeyByMpan?.get(mp.mpan) ?? null
        if (key !== null && (latestKey === null || key > latestKey)) latestKey = key
      }
      if (latestKey === null) return 0
      return rows.filter((row) => row.key <= latestKey).length
    }

    const importMpans = includedMeterPoints.filter((mp) => mp.meterType !== 'GAS' && !mp.isExport)
    const exportMpans = includedMeterPoints.filter((mp) => mp.meterType !== 'GAS' && mp.isExport)
    const gasMpans = includedMeterPoints.filter((mp) => mp.meterType === 'GAS')

    const importFigures = sumFigures(importMpans)
    // Export kwh/usageCost are stored sign-flipped negative (see useUsagePeriodData) so
    // exported energy displays as negative kWh/cost, same as it nets against import in the
    // chart/table TOTAL column - that's also what makes summing straight into netFigures below
    // correct, rather than needing to subtract it.
    const exportFigures = sumFigures(exportMpans)
    const gasFigures = sumFigures(gasMpans)

    return {
      importMpans,
      exportMpans,
      gasMpans,
      importFigures,
      exportFigures,
      gasFigures,
      netFigures: addFigures(addFigures(importFigures, exportFigures), gasFigures),
      importHasOffPeak: importMpans.some((mp) => offPeakAvailableByMpan?.get(mp.mpan) ?? false),
      importStdChgDays: stdChgDayCount(importMpans),
      exportStdChgDays: stdChgDayCount(exportMpans),
      gasStdChgDays: stdChgDayCount(gasMpans),
      importPeriodCount: usagePeriodCount(importMpans),
      exportPeriodCount: usagePeriodCount(exportMpans),
      gasPeriodCount: usagePeriodCount(gasMpans),
      netPeriodCount: usagePeriodCount(includedMeterPoints),
    }
  }, [
    enableInsights,
    includedMeterPoints,
    totalsByMpan,
    offPeakAvailableByMpan,
    latestPeriodKeyByMpan,
    stdChgDaysByMpan,
    rows,
  ])

  // Refuses to turn a view off if it's the only one currently on, so the user can never end
  // up with every view hidden. Chart/Insights check tableVisible (not showTable) so that on
  // mobile - where the table is never shown at all - it can't be "held onto" as a phantom
  // fallback that would leave the screen blank.
  function toggleChart() {
    setShowChart((current) => (current && !tableVisible && !showInsights ? current : !current))
  }

  function toggleTable() {
    setShowTable((current) => (current && !showChart && !showInsights ? current : !current))
  }

  function toggleInsights() {
    setShowInsights((current) => (current && !showChart && !tableVisible ? current : !current))
  }

  function toggleMpan(mpan: string) {
    setSelectedMpans((current) => {
      const next = new Set(current)
      if (next.has(mpan)) next.delete(mpan)
      else next.add(mpan)
      return next
    })
  }

  function toggleSolar() {
    setShowSolar((current) => !current)
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
            disabled={showChart && !tableVisible && !showInsights}
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
          {enableInsights && (
            <button
              type="button"
              className={showInsights ? 'active' : ''}
              onClick={toggleInsights}
              disabled={showInsights && !showChart && !tableVisible}
              aria-pressed={showInsights}
              aria-label="Show insights"
              title="Show insights"
            >
              <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true">
                <path
                  d="M10 1.7a5.4 5.4 0 0 0-3.25 9.7c.58.44.95 1.12.95 1.85v.55h4.6v-.55c0-.73.37-1.41.95-1.85A5.4 5.4 0 0 0 10 1.7Z"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.4"
                  strokeLinejoin="round"
                />
                <line x1="10" y1="5.3" x2="10" y2="8.6" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                <line x1="7.7" y1="16.2" x2="12.3" y2="16.2" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
                <line x1="8.3" y1="18" x2="11.7" y2="18" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
              </svg>
            </button>
          )}
          {!isMobile && (
            <button
              type="button"
              className={showTable ? 'active' : ''}
              onClick={toggleTable}
              disabled={showTable && !showChart && !showInsights}
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
          )}
        </div>
      </div>

      {((meterPoints && meterPoints.length > 0) || solarAvailable) && (
        <div className="usage-page__mpan-toggles">
          {meterPoints?.map((mp) => (
            <label key={mp.mpan} className="usage-page__mpan-toggle">
              <input
                type="checkbox"
                checked={selectedMpans?.has(mp.mpan) ?? true}
                onChange={() => toggleMpan(mp.mpan)}
              />
              {meterPointLabel(mp)}
            </label>
          ))}
          {solarAvailable && (
            <label className="usage-page__mpan-toggle">
              <input type="checkbox" checked={showSolar} onChange={toggleSolar} />
              Solar
            </label>
          )}
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
                {solarActive && solarTotalKwh !== null && (
                  <span className="usage-page__chart-total-solar"> · Solar: {formatKwh(solarTotalKwh)} kWh</span>
                )}
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
              solar={
                solarActive && solarByKey
                  ? {
                      byKey: solarByKey,
                      unit: solarUnit,
                      // A kWh overlay shares the primary axis when the bars are also kWh, but
                      // needs its own axis on the £ view (per the cost-view design) or whenever
                      // the overlay itself is in Watts (the Day page's power curve, which is
                      // never comparable to kWh/£ on a shared scale regardless of chart view).
                      useSecondaryAxis: solarUnit === 'W' || chartView === 'cost',
                    }
                  : undefined
              }
            />
          )}
        </div>
      )}

      {!error && rows && hasAnyUsage && enableInsights && showInsights && insightsData && (
        <div className="usage-page__insights-section">
          <h2 className="usage-page__insights-title">
            Summary{periodSummaryLabel ? ` for ${periodSummaryLabel}` : ''}
          </h2>

          {insightsData.importMpans.length > 0 && (
            <div className="usage-page__insights-category">
              <h3 className="usage-page__insights-subheading">Elec (Import)</h3>
              <div className="usage-page__stat-grid">
                <StatTile
                  label="Off-peak Usage"
                  value={
                    insightsData.importHasOffPeak
                      ? formatKwhCostLine(insightsData.importFigures.kwhOffPeak, insightsData.importFigures.costOffPeak)
                      : '–'
                  }
                />
                <StatTile
                  label="Peak Usage"
                  value={
                    insightsData.importHasOffPeak
                      ? formatKwhCostLine(
                          insightsData.importFigures.kwh - insightsData.importFigures.kwhOffPeak,
                          insightsData.importFigures.usageCost - insightsData.importFigures.costOffPeak,
                        )
                      : '–'
                  }
                />
                {insightsData.importFigures.stdChg !== 0 && (
                  <StatTile
                    label="Standing charge"
                    value={formatStdChargeLine(insightsData.importStdChgDays, insightsData.importFigures.stdChg)}
                  />
                )}
                <StatTile
                  label="Total"
                  value={formatKwhAndCostLine(insightsData.importFigures.kwh, insightsData.importFigures.total)}
                />
                <StatTile
                  label={`Average per ${insightsPeriodLabel}`}
                  value={formatAveragePerPeriodLine(
                    insightsData.importFigures.kwh,
                    insightsData.importFigures.total,
                    insightsData.importPeriodCount,
                  )}
                />
                <StatTile
                  label="Avg Import Rate"
                  value={`${formatRatePence(insightsData.importFigures.kwh, insightsData.importFigures.usageCost)}/kWh`}
                />
                <StatTile
                  label="Off-peak %"
                  value={insightsData.importHasOffPeak ? formatPercent(offPeakPct(insightsData.importFigures)) : '–'}
                />
              </div>
            </div>
          )}

          {insightsData.exportMpans.length > 0 && (
            <div className="usage-page__insights-category">
              <h3 className="usage-page__insights-subheading">Elec (Export)</h3>
              <div className="usage-page__stat-grid">
                <StatTile
                  label="Usage"
                  value={formatKwhCostLine(insightsData.exportFigures.kwh, insightsData.exportFigures.usageCost)}
                />
                {insightsData.exportFigures.stdChg !== 0 && (
                  <StatTile
                    label="Standing charge"
                    value={formatStdChargeLine(insightsData.exportStdChgDays, insightsData.exportFigures.stdChg)}
                  />
                )}
                <StatTile
                  label="Total"
                  value={formatKwhAndCostLine(insightsData.exportFigures.kwh, insightsData.exportFigures.total)}
                />
                <StatTile
                  label={`Average per ${insightsPeriodLabel}`}
                  value={formatAveragePerPeriodLine(
                    insightsData.exportFigures.kwh,
                    insightsData.exportFigures.total,
                    insightsData.exportPeriodCount,
                  )}
                />
              </div>
            </div>
          )}

          {insightsData.gasMpans.length > 0 && (
            <div className="usage-page__insights-category">
              <h3 className="usage-page__insights-subheading">Gas</h3>
              <div className="usage-page__stat-grid">
                <StatTile
                  label="Usage"
                  value={formatKwhCostLine(insightsData.gasFigures.kwh, insightsData.gasFigures.usageCost)}
                />
                {insightsData.gasFigures.stdChg !== 0 && (
                  <StatTile
                    label="Standing charge"
                    value={formatStdChargeLine(insightsData.gasStdChgDays, insightsData.gasFigures.stdChg)}
                  />
                )}
                <StatTile
                  label="Total"
                  value={formatKwhAndCostLine(insightsData.gasFigures.kwh, insightsData.gasFigures.total)}
                />
                <StatTile
                  label={`Average per ${insightsPeriodLabel}`}
                  value={formatAveragePerPeriodLine(
                    insightsData.gasFigures.kwh,
                    insightsData.gasFigures.total,
                    insightsData.gasPeriodCount,
                  )}
                />
              </div>
            </div>
          )}

          {solarActive && solarTotalKwh !== null && (
            <div className="usage-page__insights-category">
              <h3 className="usage-page__insights-subheading">Solar</h3>
              <div className="usage-page__stat-grid">
                <StatTile label="Total" value={formatKwhOnlyLine(solarTotalKwh)} />
                <StatTile
                  label={`Average per ${insightsPeriodLabel}`}
                  value={formatSolarAveragePerPeriodLine(solarTotalKwh, solarPeriodCount)}
                />
              </div>
            </div>
          )}

          {(insightsData.importMpans.length > 0 ||
            insightsData.exportMpans.length > 0 ||
            insightsData.gasMpans.length > 0) && (
            <div className="usage-page__insights-category">
              <h3 className="usage-page__insights-subheading">Net Total</h3>
              <div className="usage-page__stat-grid">
                <StatTile
                  label="Total"
                  value={formatKwhAndCostLine(insightsData.netFigures.kwh, insightsData.netFigures.total)}
                />
                <StatTile
                  label={`Average per ${insightsPeriodLabel}`}
                  value={formatAveragePerPeriodLine(
                    insightsData.netFigures.kwh,
                    insightsData.netFigures.total,
                    insightsData.netPeriodCount,
                  )}
                />
              </div>
            </div>
          )}

          {insightsData.importMpans.length === 0 &&
            insightsData.exportMpans.length === 0 &&
            insightsData.gasMpans.length === 0 &&
            !(solarActive && solarTotalKwh !== null) && (
              <p className="usage-page__insights-empty">Select an MPAN above to see insights.</p>
            )}
        </div>
      )}

      {!error && rows && hasAnyUsage && tableVisible && (
        <>
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
                          <td className="usage-page__group-start"><KwhCell kwh={f.kwh} intervalCount={f.intervalCount} missingIntervalCount={f.missingIntervalCount} /></td>
                          {offPeakAvailableByMpan?.get(mp.mpan) && <td>{formatPercent(offPeakPct(f))}</td>}
                          <td>{formatRate(f.avgRate)}</td>
                          <td>{formatCost(f.usageCost)}</td>
                          <td>{formatCost(f.stdChg)}</td>
                          <td>{formatCost(f.total)}</td>
                        </Fragment>
                      )
                    })}
                    <td className="usage-page__group-start"><KwhCell kwh={total.kwh} intervalCount={total.intervalCount} missingIntervalCount={total.missingIntervalCount} /></td>
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
                        <td className="usage-page__group-start"><KwhCell kwh={f.kwh} intervalCount={f.intervalCount} missingIntervalCount={f.missingIntervalCount} /></td>
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
                        <td className="usage-page__group-start"><KwhCell kwh={total.kwh} intervalCount={total.intervalCount} missingIntervalCount={total.missingIntervalCount} /></td>
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
                        <td className="usage-page__group-start"><KwhCell kwh={f.kwh} intervalCount={f.intervalCount} missingIntervalCount={f.missingIntervalCount} /></td>
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
                    let intervalCount = 0
                    let missingIntervalCount = 0
                    for (const mp of includedMeterPoints) {
                      const f = averageFigures(totalsByMpan.get(mp.mpan) ?? emptyFigures(), periodsWithData(mp.mpan))
                      kwh += f.kwh
                      total += f.total
                      intervalCount += f.intervalCount
                      missingIntervalCount += f.missingIntervalCount
                    }
                    return (
                      <>
                        <td className="usage-page__group-start">
                          <KwhCell kwh={kwh} intervalCount={intervalCount} missingIntervalCount={missingIntervalCount} />
                        </td>
                        <td>{formatCost(total)}</td>
                      </>
                    )
                  })()}
                </tr>
              </tfoot>
            )}
          </table>
        </div>
        {tableHasMissing && (
          <p className="usage-page__missing-legend">
            Periods with missing data are shown in{' '}
            <span className="usage-page__missing-legend-highlight">GREY</span>
          </p>
        )}
        </>
      )}
    </section>
  )
}
