import { Fragment } from 'react'
import {
  Bar,
  CartesianGrid,
  Cell,
  ComposedChart,
  Line,
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

const SOLAR_SERIES_NAME_KWH = 'Solar'
const SOLAR_SERIES_NAME_KW = 'Solar'

export interface SolarOverlayProps {
  // Period key (PeriodRow.key) -> value - kWh for the period-based pages, kW for the Day page's
  // intraday power curve. Opaque to this component either way; it just plots whatever's here.
  byKey: Map<string, number>
  unit: 'kWh' | 'kW'
  // True whenever the overlay's unit doesn't share a scale with the bars' own axis - only the £
  // view today, since both kWh and kW sit close enough to the bars' own kWh magnitude to share
  // an axis, but £ never does.
  useSecondaryAxis: boolean
}

export interface BatteryOverlayProps {
  // Period key -> battery state of charge, 0-100 - always the Day page's intraday curve (no
  // persisted period-level figure exists, unlike solar). Always plotted on its own fixed 0-100
  // axis (see the "battery" YAxis below) rather than sharing with the bars or with solar - a
  // percentage isn't on the same scale as either kWh/£ or kW.
  byKey: Map<string, number>
}

interface UsageBarChartProps {
  rows: PeriodRow[]
  meterPoints: MeterPoint[]
  metric: ChartMetric
  // Which MPANs have a peak/off-peak split for the shown period - undefined/missing means no
  // split, same as the table column that drives this same flag.
  offPeakAvailableByMpan?: Map<string, boolean>
  solar?: SolarOverlayProps
  battery?: BatteryOverlayProps
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

function minPointSizeForFullyMissing(data: Record<string, number | string | boolean | null>[], key: string) {
  return (_value: number | undefined | null, index: number) => (data[index]?.[key] ? MIN_MISSING_BAR_PX : 0)
}

// Rounds a positive value up to a "clean" step (1/2/5 x 10^n) - approximates the padding
// Recharts' own default ("nice") domain would pick, so an axis given an explicit domain (see
// below) still lands on round tick numbers instead of the raw data max/min.
function niceCeil(value: number): number {
  if (value <= 0) return 0
  const exponent = Math.floor(Math.log10(value))
  const magnitude = 10 ** exponent
  const fraction = value / magnitude
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10
  return niceFraction * magnitude
}

function niceFloor(value: number): number {
  return -niceCeil(-value)
}

// Defensive second layer for axis tick labels, independent of how clean the domain bounds fed
// into Recharts are - its own "nice" tick step computation can still land a tick a hair off a
// round number (e.g. a stray 2e-13 instead of exactly 0). Snapping anything within float-noise
// distance of zero to exactly 0, and otherwise rounding to 2dp, keeps every displayed tick clean
// without hiding genuine sub-integer values (fractional £ totals in particular).
function roundTick(value: number): number {
  if (Math.abs(value) < 1e-6) return 0
  return Math.round(value * 100) / 100
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

function formatSolarValue(value: number, unit: 'kWh' | 'kW'): string {
  return `${value.toFixed(2)} ${unit}`
}

const BATTERY_SERIES_NAME = 'Battery'

function formatBatteryValue(value: number): string {
  return `${Math.round(value)}%`
}

export default function UsageBarChart({ rows, meterPoints, metric, offPeakAvailableByMpan, solar, battery }: UsageBarChartProps) {
  const isMobile = useIsMobile()
  const solarSeriesName = solar?.unit === 'kW' ? SOLAR_SERIES_NAME_KW : SOLAR_SERIES_NAME_KWH
  const data = rows.map((row) => {
    const point: Record<string, number | string | boolean | null> = { dayLabel: row.chartLabel }
    if (solar) {
      point.solarValue = solar.byKey.get(row.key) ?? null
    }
    if (battery) {
      point.batteryValue = battery.byKey.get(row.key) ?? null
    }
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

  // With a secondary axis, Recharts scales each YAxis independently from its own data, which
  // essentially never puts their zero lines at the same height (see e.g. the cost view: bars
  // span a small negative-to-positive £ range, while solar kWh only ever spans zero-to-positive -
  // left ends up with 0 in the middle, right with 0 at the very bottom). Both axes are given an
  // explicit, jointly-computed domain instead so their zero points align exactly; the primary
  // axis's own natural range is untouched otherwise (padding matches niceCeil, not stretched),
  // and only the secondary axis gets an invisible negative floor added below its real zero,
  // sized so it lands at the same fractional height as the primary axis's zero. Battery's own
  // axis needs the same treatment - its top (100%) is fixed, but its floor grows the same way
  // solar's does, so 0% lines up with the primary axis's zero too.
  let primaryDomain: [number, number] | undefined
  let solarDomain: [number, number] | undefined
  let solarTicks: number[] | undefined
  let batteryDomain: [number, number] | undefined
  if (solar?.useSecondaryAxis || battery) {
    const groupsByStackId = new Map<string, MeterPoint[]>()
    for (const mp of meterPoints) {
      const id = stackIdFor(mp)
      const group = groupsByStackId.get(id) ?? []
      group.push(mp)
      groupsByStackId.set(id, group)
    }

    let primMaxRaw = 0
    let primMinRaw = 0
    for (const point of data) {
      for (const group of groupsByStackId.values()) {
        let pos = 0
        let neg = 0
        for (const mp of group) {
          const hasSplit = offPeakAvailableByMpan?.get(mp.mpan) ?? false
          const valueKeys = hasSplit ? [offPeakKey(mp.mpan), peakKey(mp.mpan)] : [usageKey(mp.mpan)]
          const keys = metric === 'cost' ? [stdChgKey(mp.mpan), ...valueKeys] : valueKeys
          for (const key of keys) {
            const value = point[key]
            if (typeof value !== 'number') continue
            if (value >= 0) pos += value
            else neg += value
          }
        }
        if (pos > primMaxRaw) primMaxRaw = pos
        if (neg < primMinRaw) primMinRaw = neg
      }
    }
    // When solar shares the primary axis (not useSecondaryAxis), it would otherwise be part of
    // Recharts' own auto-domain for that axis - folded in here so an explicit primaryDomain
    // (now always set whenever battery needs one) doesn't clip it back down to the bars' own
    // range.
    if (solar && !solar.useSecondaryAxis) {
      for (const value of solar.byKey.values()) {
        if (value > primMaxRaw) primMaxRaw = value
      }
    }

    const primMax = niceCeil(primMaxRaw || 1)
    const primMin = primMinRaw < 0 ? niceFloor(primMinRaw) : 0
    // Fraction of the primary axis that sits below zero - the secondary axis's own negative
    // floor is sized so its zero lands at this same fraction, even though solar/battery
    // themselves never go negative.
    const belowZeroFraction = primMax > primMin ? -primMin / (primMax - primMin) : 0
    // The rounding below undoes the one domain value not already snapped to a round number by
    // niceCeil/niceFloor, so it (and the near-zero tick Recharts' own "nice" tick step then
    // derives from it) don't inherit float noise like -1999.9999999999998 or a stray 2e-13 "zero".
    function alignedFloor(max: number): number {
      return belowZeroFraction > 0 ? Math.round(-(belowZeroFraction / (1 - belowZeroFraction)) * max) : 0
    }

    primaryDomain = [primMin, primMax]

    if (solar?.useSecondaryAxis) {
      let solarMaxRaw = 0
      for (const value of solar.byKey.values()) {
        if (value > solarMaxRaw) solarMaxRaw = value
      }
      const solarMax = niceCeil(solarMaxRaw || 1)
      solarDomain = [alignedFloor(solarMax), solarMax]
      // Explicit ticks, same reasoning as BATTERY_TICKS below - without this, Recharts' own
      // "nice" tick step spans the whole domain including the invisible negative floor, which
      // surfaces a negative tick (e.g. "-6") even though solar itself never goes negative.
      solarTicks = [0, solarMax / 4, solarMax / 2, (solarMax * 3) / 4, solarMax]
    }

    if (battery) {
      batteryDomain = [alignedFloor(100), 100]
    }
  }

  // Battery's real range is always 0-100 - fixed ticks so the invisible negative floor added
  // above (to align 0% with the other axes' zero) never grows a meaningless negative-percent
  // tick label.
  const BATTERY_TICKS = [0, 25, 50, 75, 100]

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
    ...(metric === 'cost' ? [{ label: 'Std charge', color: 'var(--chart-stdchg)' }] : []),
    ...(solar ? [{ label: solarSeriesName, color: 'var(--chart-solar)' }] : []),
    ...(battery ? [{ label: BATTERY_SERIES_NAME, color: 'var(--chart-battery)' }] : []),
  ]

  const hasAnyMissing = data.some((point) => meterPoints.some((mp) => point[missingKey(mp.mpan)]))

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={360}>
        <ComposedChart data={data} stackOffset="sign" margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
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
            domain={primaryDomain}
            tick={{ fill: 'var(--text)', fontSize: 11 }}
            tickFormatter={(value: number) => (metric === 'kwh' ? `${roundTick(value)}` : `£${roundTick(value)}`)}
            width={56}
          />
          {solar?.useSecondaryAxis && (
            <YAxis
              yAxisId="solar"
              orientation="right"
              domain={solarDomain}
              ticks={solarTicks}
              tick={{ fill: 'var(--chart-solar)', fontSize: 11 }}
              tickFormatter={(value: number) => `${roundTick(value)}`}
              width={56}
            />
          )}
          {battery && (
            <YAxis
              yAxisId="battery"
              orientation="right"
              domain={batteryDomain}
              ticks={BATTERY_TICKS}
              tick={{ fill: 'var(--chart-battery)', fontSize: 11 }}
              tickFormatter={(value: number) => `${value}%`}
              width={40}
            />
          )}
          <ReferenceLine y={0} stroke="var(--text)" />
          {/* The tooltip's own box is wide enough to cover most of a phone-width chart under
              the finger that triggered it, so it's dropped entirely on mobile rather than shown. */}
          {!isMobile && (
            <Tooltip
              formatter={(value, name) => {
                if (solar && name === solarSeriesName) return [formatSolarValue(Number(value), solar.unit), name]
                if (battery && name === BATTERY_SERIES_NAME) return [formatBatteryValue(Number(value)), name]
                return [formatValue(Number(value), metric), name]
              }}
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
          {solar && (
            <Line
              yAxisId={solar.useSecondaryAxis ? 'solar' : undefined}
              type="monotone"
              dataKey="solarValue"
              stroke="var(--chart-solar)"
              strokeWidth={3}
              dot={false}
              connectNulls={false}
              isAnimationActive={false}
              name={solarSeriesName}
            />
          )}
          {battery && (
            <Line
              yAxisId="battery"
              type="monotone"
              dataKey="batteryValue"
              stroke="var(--chart-battery)"
              strokeWidth={3}
              dot={false}
              connectNulls={false}
              isAnimationActive={false}
              name={BATTERY_SERIES_NAME}
            />
          )}
        </ComposedChart>
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
