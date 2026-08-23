import { Fragment } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { MeterPoint } from '../api/types'
import { useIsMobile } from '../hooks/useIsMobile'
import { meterPointLabel, type PeriodRow } from '../pages/usageShared'
import './UsageBarChart.css'

const MPAN_COLORS = ['var(--chart-mpan-1)', 'var(--chart-mpan-2)', 'var(--chart-mpan-3)']
const MPAN_OFFPEAK_COLORS = [
  'var(--chart-mpan-1-offpeak)',
  'var(--chart-mpan-2-offpeak)',
  'var(--chart-mpan-3-offpeak)',
]

type HatchKind = 'mpan' | 'offpeak'

// A bar segment built from data-integrity-check placeholder intervals (see
// MpanFigures.missingIntervalCount) is rendered with a cross-hatch texture instead of a flat
// fill, so it still reads as a bar - just visibly provisional - rather than either blending in
// as ordinary data or disappearing. The hatch is drawn in the segment's own series color
// (not a fixed "warning" color) so the colour scheme stays the same regardless of whether a
// period is missing data - only the texture changes.
function missingHatchId(kind: HatchKind, index: number): string {
  return `usage-bar-chart-missing-hatch-${kind}-${index}`
}

function missingHatchFill(kind: HatchKind, index: number): string {
  return `url(#${missingHatchId(kind, index)})`
}

function missingHatchPatterns(colors: string[], kind: HatchKind) {
  return colors.map((color, index) => (
    <pattern
      key={`${kind}-${index}`}
      id={missingHatchId(kind, index)}
      width="6"
      height="6"
      patternUnits="userSpaceOnUse"
      patternTransform="rotate(45)"
    >
      <rect width="6" height="6" fill={color} opacity="0.25" />
      <line x1="0" y1="0" x2="0" y2="6" stroke={color} strokeWidth="1.5" />
      <line x1="0" y1="0" x2="6" y2="0" stroke={color} strokeWidth="1.5" />
    </pattern>
  ))
}

export type ChartMetric = 'kwh' | 'cost'

interface UsageBarChartProps {
  rows: PeriodRow[]
  meterPoints: MeterPoint[]
  metric: ChartMetric
  // Which MPANs have a peak/off-peak split for the shown period - undefined/missing means no
  // split, same as the table column that drives this same flag.
  offPeakAvailableByMpan?: Map<string, boolean>
}

function usageKey(mpan: string): string {
  return `${mpan}_usage`
}

function offPeakKey(mpan: string): string {
  return `${mpan}_offpeak`
}

function peakKey(mpan: string): string {
  return `${mpan}_peak`
}

function stdChgKey(mpan: string): string {
  return `${mpan}_stdChg`
}

// Standing charge deliberately has no equivalent - it's always known/charged regardless of
// whether usage data landed for that period, so it's never hatched even when the usage segment
// stacked alongside it is.
function missingKey(mpan: string): string {
  return `${mpan}_missing`
}

function fullyMissingKey(mpan: string): string {
  return `${mpan}_fullyMissing`
}

// A period with zero recorded intervals renders a zero-height bar, so a fully-missing period
// (every interval a placeholder) would otherwise be visually indistinguishable from a period
// that's genuinely zero usage. minPointSize gives it a sliver of height instead - but only for
// bars flagged fully missing, so genuine zero-usage bars (e.g. no gas used that day) stay flat.
const MIN_MISSING_BAR_PX = 3

// A truly zero value can't be given a signed pixel height by minPointSize alone: stackOffset
// "sign" (see stackIdFor below) buckets a point into the positive or negative stack purely by
// whether its raw value is >= 0, before minPointSize ever runs - so an export MPAN's exact 0
// still lands in the positive stack, on top of import, however minPointSize is signed afterwards.
// EXPORT_ZERO_EPSILON nudges a fully-missing export value just below zero so it lands in the
// negative stack at the true baseline instead; formatValue below hides the resulting "-0.00".
const EXPORT_ZERO_EPSILON = -1e-6

function minPointSizeForFullyMissing(data: Record<string, number | string | boolean>[], key: string) {
  return (_value: number | undefined | null, index: number) => (data[index]?.[key] ? MIN_MISSING_BAR_PX : 0)
}

// Electricity import and export share a stackId so they render as one bar per day rather
// than two side-by-side ones. Combined with stackOffset="sign" on the BarChart below, values
// sharing a stackId diverge by sign from a common zero baseline - import's positive values
// stack upward, export's negative values stack downward, in the same bar. Gas keeps its own
// stackId per meter point, rendering as a separate bar alongside it.
function stackIdFor(mp: MeterPoint): string {
  return mp.meterType === 'ELEC' ? 'electricity' : mp.mpan
}

function formatValue(value: number, metric: ChartMetric): string {
  // Rounds away the EXPORT_ZERO_EPSILON nudge above so a fully-missing period's tooltip reads
  // "0.00", not "-0.00".
  const clean = Math.abs(value) < 0.005 ? 0 : value
  return metric === 'kwh' ? `${clean.toFixed(2)} kWh` : `£${clean.toFixed(2)}`
}

export default function UsageBarChart({ rows, meterPoints, metric, offPeakAvailableByMpan }: UsageBarChartProps) {
  const isMobile = useIsMobile()
  const data = rows.map((row) => {
    const point: Record<string, number | string | boolean> = { dayLabel: row.label }
    for (const mp of meterPoints) {
      const figures = row.byMpan[mp.mpan]
      const hasSplit = offPeakAvailableByMpan?.get(mp.mpan) ?? false
      const intervalCount = figures?.intervalCount ?? 0
      const missingIntervalCount = figures?.missingIntervalCount ?? 0
      const isFullyMissing = intervalCount > 0 && missingIntervalCount === intervalCount
      point[missingKey(mp.mpan)] = missingIntervalCount > 0
      point[fullyMissingKey(mp.mpan)] = isFullyMissing
      const exportEpsilon = isFullyMissing && mp.isExport ? EXPORT_ZERO_EPSILON : 0
      if (metric === 'cost') {
        point[stdChgKey(mp.mpan)] = figures ? figures.stdChg : 0
      }
      if (hasSplit) {
        const total = figures ? (metric === 'kwh' ? figures.kwh : figures.usageCost) : 0
        const offPeak = figures ? (metric === 'kwh' ? figures.kwhOffPeak : figures.costOffPeak) : 0
        point[offPeakKey(mp.mpan)] = offPeak
        point[peakKey(mp.mpan)] = total - offPeak + exportEpsilon
      } else {
        point[usageKey(mp.mpan)] = (figures ? (metric === 'kwh' ? figures.kwh : figures.usageCost) : 0) + exportEpsilon
      }
    }
    return point
  })

  // Thin out x-axis labels for a full month so they don't overlap; every day is still a
  // separate bar group, only the tick labels are skipped. Mobile gets a much lower cap since
  // the same label count that fits a desktop-width chart collides at phone width.
  const maxLabels = isMobile ? 6 : 15
  const tickInterval = data.length > maxLabels ? Math.ceil(data.length / maxLabels) - 1 : 0

  // A hand-built legend rather than recharts' <Legend>: one entry per MPAN (two - peak and
  // off-peak - when the tariff has a split) plus a single shared "Standing charge" entry,
  // instead of one duplicated per MPAN's stacked bar.
  const legendEntries = [
    ...meterPoints.flatMap((mp, index) => {
      const label = meterPointLabel(mp)
      if (!(offPeakAvailableByMpan?.get(mp.mpan) ?? false)) {
        return [{ label, color: MPAN_COLORS[index % MPAN_COLORS.length] }]
      }
      return [
        { label: `${label} – Peak`, color: MPAN_COLORS[index % MPAN_COLORS.length] },
        { label: `${label} – Off-peak`, color: MPAN_OFFPEAK_COLORS[index % MPAN_OFFPEAK_COLORS.length] },
      ]
    }),
    ...(metric === 'cost' ? [{ label: 'Standing charge', color: 'var(--chart-stdchg)' }] : []),
  ]

  const hasAnyMissing = data.some((point) => meterPoints.some((mp) => point[missingKey(mp.mpan)]))

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={360}>
        <BarChart data={data} stackOffset="sign" margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
          <defs>
            {missingHatchPatterns(MPAN_COLORS, 'mpan')}
            {missingHatchPatterns(MPAN_OFFPEAK_COLORS, 'offpeak')}
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis
            dataKey="dayLabel"
            tick={{ fill: 'var(--text)', fontSize: 11 }}
            interval={tickInterval}
          />
          <YAxis
            tick={{ fill: 'var(--text)', fontSize: 11 }}
            tickFormatter={(value: number) => (metric === 'kwh' ? `${value}` : `£${value}`)}
            width={56}
          />
          <ReferenceLine y={0} stroke="var(--text)" />
          {/* The tooltip's own box is wide enough to cover most of a phone-width chart under
              the finger that triggered it, so it's dropped entirely on mobile rather than shown. */}
          {!isMobile && (
            <Tooltip
              formatter={(value, name) => [formatValue(Number(value), metric), name]}
              contentStyle={{
                background: 'var(--bg)',
                border: '1px solid var(--border)',
                color: 'var(--text-h)',
                fontSize: 11,
                padding: '6px 8px',
              }}
              labelStyle={{ color: 'var(--text-h)', marginBottom: 2 }}
              itemStyle={{ padding: 0 }}
            />
          )}
          {meterPoints.map((mp, index) => {
            const hasSplit = offPeakAvailableByMpan?.get(mp.mpan) ?? false
            const label = meterPointLabel(mp)
            return (
              // Recharts tracks each stack segment's position by registration (mount) order,
              // not by <Bar> declaration order in a given render - a bar mounts once and stays
              // at its original stack position even after a later bar mounts. Keying every bar
              // with `metric` and `hasSplit` forces the whole group to remount together on
              // every toggle, so declaration order (standing charge, then off-peak, then peak -
              // bottom to top) is actually honoured each time.
              <Fragment key={mp.mpan}>
                {metric === 'cost' && (
                  <Bar
                    key={`${mp.mpan}-stdchg-${metric}-${hasSplit}`}
                    dataKey={stdChgKey(mp.mpan)}
                    stackId={stackIdFor(mp)}
                    fill="var(--chart-stdchg)"
                    name="Standing charge"
                  />
                )}
                {hasSplit ? (
                  <>
                    <Bar
                      key={`${mp.mpan}-offpeak-${metric}-${hasSplit}`}
                      dataKey={offPeakKey(mp.mpan)}
                      stackId={stackIdFor(mp)}
                      fill={MPAN_OFFPEAK_COLORS[index % MPAN_OFFPEAK_COLORS.length]}
                      name={`${label} – Off-peak`}
                    >
                      {data.map((entry, i) => (
                        <Cell
                          key={i}
                          fill={
                            entry[missingKey(mp.mpan)]
                              ? missingHatchFill('offpeak', index % MPAN_OFFPEAK_COLORS.length)
                              : MPAN_OFFPEAK_COLORS[index % MPAN_OFFPEAK_COLORS.length]
                          }
                        />
                      ))}
                    </Bar>
                    <Bar
                      key={`${mp.mpan}-peak-${metric}-${hasSplit}`}
                      dataKey={peakKey(mp.mpan)}
                      stackId={stackIdFor(mp)}
                      fill={MPAN_COLORS[index % MPAN_COLORS.length]}
                      name={`${label} – Peak`}
                      minPointSize={minPointSizeForFullyMissing(data, fullyMissingKey(mp.mpan))}
                    >
                      {data.map((entry, i) => (
                        <Cell
                          key={i}
                          fill={
                            entry[missingKey(mp.mpan)]
                              ? missingHatchFill('mpan', index % MPAN_COLORS.length)
                              : MPAN_COLORS[index % MPAN_COLORS.length]
                          }
                        />
                      ))}
                    </Bar>
                  </>
                ) : (
                  <Bar
                    key={`${mp.mpan}-usage-${metric}-${hasSplit}`}
                    dataKey={usageKey(mp.mpan)}
                    stackId={stackIdFor(mp)}
                    fill={MPAN_COLORS[index % MPAN_COLORS.length]}
                    name={label}
                    minPointSize={minPointSizeForFullyMissing(data, fullyMissingKey(mp.mpan))}
                  >
                    {data.map((entry, i) => (
                      <Cell
                        key={i}
                        fill={
                          entry[missingKey(mp.mpan)]
                            ? missingHatchFill('mpan', index % MPAN_COLORS.length)
                            : MPAN_COLORS[index % MPAN_COLORS.length]
                        }
                      />
                    ))}
                  </Bar>
                )}
              </Fragment>
            )
          })}
        </BarChart>
      </ResponsiveContainer>
      <div className="usage-bar-chart__legend">
        {legendEntries.map((entry) => (
          <span key={entry.label} className="usage-bar-chart__legend-entry">
            <span className="usage-bar-chart__legend-swatch" style={{ background: entry.color }} />
            {entry.label}
          </span>
        ))}
        {/* CSS-drawn crosshatch rather than referencing the SVG <pattern> above - a plain CSS
            background can't render an SVG pattern by url(), and this swatch needs to work
            outside the chart's own <svg> anyway. */}
        {hasAnyMissing && (
          <span className="usage-bar-chart__legend-entry">
            <span className="usage-bar-chart__legend-swatch usage-bar-chart__legend-swatch--missing" />
            Missing data
          </span>
        )}
      </div>
    </div>
  )
}
