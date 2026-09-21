import { Fragment } from 'react'
import {
  Area,
  Bar,
  CartesianGrid,
  Cell,
  ComposedChart,
  DefaultTooltipContent,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipPayloadEntry,
} from 'recharts'
import type { MeterPoint } from '../api/types'
import { useIsMobile } from '../hooks/useIsMobile'
import { meterPointLabel, type PeriodRow } from '../pages/usageShared'
import { MINUTES_PER_DAY, type SlotPoint } from './solarTodaySlots'
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
// The tooltip's stand-ins for the 5-minute solar/battery/load lines - see
// SolarOverlayProps.fineSeries.
const SOLAR_AVG_SERIES_NAME = 'Solar (30-min avg)'
const BATTERY_AVG_SERIES_NAME = 'Battery (30-min avg)'
const LOAD_AVG_SERIES_NAME = 'Load (30-min avg)'
// Id of the hidden numeric time axis the 5-minute lines are plotted against - one shared by solar,
// battery and load, since they all span the same 0-1440 minutes of the day.
const FINE_X_AXIS_ID = 'fine-time'

export interface SolarOverlayProps {
  // Period key (PeriodRow.key) -> value - kWh for the period-based pages, kW for the Day page's
  // intraday power curve. Opaque to this component either way; it just plots whatever's here.
  byKey: Map<string, number>
  unit: 'kWh' | 'kW'
  // True whenever the overlay's unit doesn't share a scale with the bars' own axis - only the £
  // view today, since both kWh and kW sit close enough to the bars' own kWh magnitude to share
  // an axis, but £ never does.
  useSecondaryAxis: boolean
  // Day page only - the same curve as byKey at its native 5-minute resolution (see
  // SolarOverlayData.solarFine). When present it's what's drawn as the solar line, on its own
  // hidden numeric time axis so it isn't forced onto the half-hourly bars' category positions;
  // byKey then only feeds the tooltip, whose per-half-hour figures match the bars.
  fineSeries?: SlotPoint[]
}

export interface BatteryOverlayProps {
  // Period key -> battery state of charge, 0-100 - always the Day page's intraday curve (no
  // persisted period-level figure exists, unlike solar). Always plotted on its own fixed 0-100
  // axis (see the "battery" YAxis below) rather than sharing with the bars or with solar - a
  // percentage isn't on the same scale as either kWh/£ or kW.
  byKey: Map<string, number>
  // Same meaning as SolarOverlayProps.fineSeries.
  fineSeries?: SlotPoint[]
}

export interface LoadOverlayProps {
  // Period key -> house load consumption in kW - always the Day page's intraday curve (no
  // persisted period-level figure exists, unlike solar). Always kW, unlike SolarOverlayProps,
  // since there's no period-based (kWh) view of it to support.
  byKey: Map<string, number>
  // Same meaning as SolarOverlayProps.useSecondaryAxis - shares the "solar" kW axis with solar
  // when one's in use, so both kW curves read off the same scale.
  useSecondaryAxis: boolean
  // Same meaning as SolarOverlayProps.fineSeries.
  fineSeries?: SlotPoint[]
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
  load?: LoadOverlayProps
  // Period keys (PeriodRow.key) that fall within a configured happy-hour window - Day page
  // only, since happy hours are entered as specific date/times rather than a recurring
  // time-of-day. Rendered as a shaded background band rather than per-bar coloring (unlike
  // peak/off-peak) since a happy hour is a schedule overlay, not a property of the usage itself.
  happyHourKeys?: Set<string>
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

function happyHourKey(mpan: string): string {
  return `${mpan}_happyHour`
}

// Happy hour is a third bucket beside off-peak/peak for electricity import only - the same scope
// as the insights' Happy Hour cards; export earns rather than costs, and gas has no happy hours.
function canHaveHappyHour(mp: MeterPoint): boolean {
  return mp.meterType !== 'GAS' && !mp.isExport
}

// Subtracting one bucket from a total can leave float noise like -1.4e-17, which stackOffset
// "sign" would drop into the negative stack; snap those to a true zero.
function snapTinyToZero(value: number): number {
  return Math.abs(value) < 1e-9 ? 0 : value
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

// Shared look for the solar/battery/load overlay series: the line with a translucent fill beneath
// it. Recharts draws areas at layer 100, behind the bars (300), where the opaque bars would simply
// cover the fill - so it's lifted to just in front of them, and kept translucent so the bars
// still read through it. baseValue 0 anchors the fill to the zero line rather than to the bottom
// of the (sometimes negative-floored, see alignedFloor) axis domain.
const OVERLAY_FILL_OPACITY = 0.16
const OVERLAY_Z_INDEX = 350

function overlayAreaProps(color: string) {
  return {
    type: 'monotone',
    stroke: color,
    strokeWidth: 3,
    fill: color,
    fillOpacity: OVERLAY_FILL_OPACITY,
    baseValue: 0,
    dot: false,
    connectNulls: false,
    isAnimationActive: false,
    zIndex: OVERLAY_Z_INDEX,
  } as const
}

// A series drawn at 5-minute resolution whose tooltip entry has to be added by hand: `byLabel` is
// its half-hour average per bar, keyed by the label the tooltip is given for a hovered bar.
interface FineAverage {
  name: string
  color: string
  byLabel: Map<string, number>
}

// A period with no happy-hour usage would otherwise list "Happy hour : 0.00" in its tooltip - on
// the Day page that's every bar outside the window - so those zero entries are dropped.
function withoutZeroHappyHour(payload: readonly TooltipPayloadEntry[]): TooltipPayloadEntry[] {
  return payload.filter(
    (entry) => !(typeof entry.dataKey === 'string' && entry.dataKey.endsWith('_happyHour') && !Number(entry.value)),
  )
}

// Appends the hovered bar's half-hour average for each 5-minute series to a tooltip's entries (a
// series with no average for that bar - e.g. after its last reading - is left out).
function withFineAverages(
  payload: readonly TooltipPayloadEntry[],
  label: string | number | undefined,
  averages: FineAverage[],
): TooltipPayloadEntry[] {
  const extra: TooltipPayloadEntry[] = []
  for (const average of averages) {
    const value = label !== undefined ? average.byLabel.get(String(label)) : undefined
    if (value === undefined) continue
    extra.push({
      name: average.name,
      value,
      color: average.color,
      dataKey: average.name,
      graphicalItemId: average.name,
    })
  }
  return [...payload, ...extra]
}

// Every value a line can reach, for sizing the axis it sits on - includes the 5-minute series when
// there is one, whose peaks run higher than the half-hour averages in byKey and would otherwise
// poke out of the top of an axis sized from byKey alone.
function plotValues(overlay: { byKey: Map<string, number>; fineSeries?: SlotPoint[] }): number[] {
  const values = [...overlay.byKey.values()]
  for (const point of overlay.fineSeries ?? []) {
    if (point.value !== null) values.push(point.value)
  }
  return values
}

// The 5-minute line's data: its own points rather than the chart's 48 rows, keyed by `valueKey`.
function fineLineData(series: SlotPoint[], valueKey: string): Record<string, number | null>[] {
  return series.map((point) => ({ minutes: point.minutes, [valueKey]: point.value }))
}

// Half-hour value per bar label - what the tooltip reports for a 5-minute series (see
// FineAverage).
function averageByLabel(rows: PeriodRow[], byKey: Map<string, number>): Map<string, number> {
  const result = new Map<string, number>()
  for (const row of rows) {
    const value = byKey.get(row.key)
    if (value !== undefined) result.set(row.chartLabel, value)
  }
  return result
}

const BATTERY_SERIES_NAME = 'Battery'
const LOAD_SERIES_NAME = 'Load'

function formatBatteryValue(value: number): string {
  return `${Math.round(value)}%`
}

function formatLoadValue(value: number): string {
  return `${value.toFixed(2)} kW`
}

export default function UsageBarChart({ rows, meterPoints, metric, offPeakAvailableByMpan, solar, battery, load, happyHourKeys }: UsageBarChartProps) {
  const isMobile = useIsMobile()
  const solarSeriesName = solar?.unit === 'kW' ? SOLAR_SERIES_NAME_KW : SOLAR_SERIES_NAME_KWH
  // Which lines are drawn at 5-minute resolution (Day page) - each gets a hand-built tooltip
  // entry with its half-hour average, see the Tooltip's content.
  const fineAverages: FineAverage[] = []
  if (solar?.fineSeries) {
    fineAverages.push({ name: SOLAR_AVG_SERIES_NAME, color: 'var(--chart-solar)', byLabel: averageByLabel(rows, solar.byKey) })
  }
  if (battery?.fineSeries) {
    fineAverages.push({ name: BATTERY_AVG_SERIES_NAME, color: 'var(--chart-battery)', byLabel: averageByLabel(rows, battery.byKey) })
  }
  if (load?.fineSeries) {
    fineAverages.push({ name: LOAD_AVG_SERIES_NAME, color: 'var(--chart-load)', byLabel: averageByLabel(rows, load.byKey) })
  }
  const hasFineSeries = fineAverages.length > 0
  const data = rows.map((row) => {
    const point: Record<string, number | string | boolean | null> = { dayLabel: row.chartLabel }
    if (solar) {
      point.solarValue = solar.byKey.get(row.key) ?? null
    }
    if (battery) {
      point.batteryValue = battery.byKey.get(row.key) ?? null
    }
    if (load) {
      point.loadValue = load.byKey.get(row.key) ?? null
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
      const total = figures ? (metric === 'kwh' ? figures.kwh : figures.usageCost) : 0
      // Happy-hour usage is its own stack segment, so it comes out of what's left for peak (or
      // for the single usage segment when there's no peak/off-peak split).
      const happyHour =
        canHaveHappyHour(mp) && figures ? (metric === 'kwh' ? figures.kwhHappyHour : figures.costHappyHour) : 0
      if (canHaveHappyHour(mp)) {
        point[happyHourKey(mp.mpan)] = happyHour
      }
      if (hasSplit) {
        const offPeak = figures ? (metric === 'kwh' ? figures.kwhOffPeak : figures.costOffPeak) : 0
        point[offPeakKey(mp.mpan)] = offPeak
        point[peakKey(mp.mpan)] = snapTinyToZero(total - offPeak - happyHour) + exportEpsilon
      } else {
        point[usageKey(mp.mpan)] = snapTinyToZero(total - happyHour) + exportEpsilon
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
  if (solar?.useSecondaryAxis || load?.useSecondaryAxis || battery) {
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
          // happyHourKey has no value for MPANs that can't have one - skipped by the typeof check.
          const valueKeys = hasSplit
            ? [offPeakKey(mp.mpan), peakKey(mp.mpan), happyHourKey(mp.mpan)]
            : [usageKey(mp.mpan), happyHourKey(mp.mpan)]
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
    // When solar/load share the primary axis (not useSecondaryAxis), they'd otherwise be part
    // of Recharts' own auto-domain for that axis - folded in here so an explicit primaryDomain
    // (now always set whenever battery needs one) doesn't clip them back down to the bars' own
    // range.
    if (solar && !solar.useSecondaryAxis) {
      for (const value of plotValues(solar)) {
        if (value > primMaxRaw) primMaxRaw = value
      }
    }
    if (load && !load.useSecondaryAxis) {
      for (const value of plotValues(load)) {
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

    if (solar?.useSecondaryAxis || load?.useSecondaryAxis) {
      // Shared kW axis - solar and load are both plotted on it (yAxisId="solar") when either
      // needs a secondary axis, so its max has to fit whichever curve reaches higher.
      let kwMaxRaw = 0
      if (solar?.useSecondaryAxis) {
        for (const value of plotValues(solar)) {
          if (value > kwMaxRaw) kwMaxRaw = value
        }
      }
      if (load?.useSecondaryAxis) {
        for (const value of plotValues(load)) {
          if (value > kwMaxRaw) kwMaxRaw = value
        }
      }
      const solarMax = niceCeil(kwMaxRaw || 1)
      solarDomain = [alignedFloor(solarMax), solarMax]
      // Explicit ticks, same reasoning as BATTERY_TICKS below - without this, Recharts' own
      // "nice" tick step spans the whole domain including the invisible negative floor, which
      // surfaces a negative tick (e.g. "-6") even though solar/load themselves never go negative.
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
    ...(load ? [{ label: LOAD_SERIES_NAME, color: 'var(--chart-load)' }] : []),
  ]

  const hasAnyMissing = data.some((point) => meterPoints.some((mp) => point[missingKey(mp.mpan)]))

  // MPANs with any happy-hour usage in the range shown - only these get a happy-hour bar segment
  // (and legend entry), so a page with no happy hours is unchanged.
  const happyHourMpans = new Set(
    meterPoints
      .filter((mp) => data.some((point) => Number(point[happyHourKey(mp.mpan)]) > 0))
      .map((mp) => mp.mpan),
  )

  // Collapses consecutive happy-hour periods into contiguous [x1, x2] bands (by chartLabel, the
  // same category values the x-axis itself plots) rather than one ReferenceArea per period, so
  // an hour-long happy hour renders as a single band instead of two abutting ones.
  const happyHourRanges: { x1: string; x2: string }[] = []
  if (happyHourKeys && happyHourKeys.size > 0) {
    let start: string | null = null
    let end: string | null = null
    for (const row of rows) {
      if (happyHourKeys.has(row.key)) {
        if (start === null) start = row.chartLabel
        end = row.chartLabel
      } else if (start !== null) {
        happyHourRanges.push({ x1: start, x2: end! })
        start = null
        end = null
      }
    }
    if (start !== null) happyHourRanges.push({ x1: start, x2: end! })
  }

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={360}>
        <ComposedChart data={data} stackOffset="sign" margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
          <defs>
            {missingHatchPatterns(MPAN_COLORS, 'mpan')}
            {missingHatchPatterns(MPAN_OFFPEAK_COLORS, 'offpeak')}
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          {happyHourRanges.map((range, index) => (
            <ReferenceArea
              key={index}
              x1={range.x1}
              x2={range.x2}
              fill="var(--chart-happy-hour)"
              fillOpacity={0.18}
              stroke="var(--chart-happy-hour)"
              strokeOpacity={0.5}
              ifOverflow="extendDomain"
            />
          ))}
          <XAxis
            dataKey="dayLabel"
            tick={{ fill: 'var(--text)', fontSize: 11 }}
            interval={tickInterval}
          />
          {hasFineSeries && (
            <XAxis
              xAxisId={FINE_X_AXIS_ID}
              type="number"
              dataKey="minutes"
              domain={[0, MINUTES_PER_DAY]}
              hide
            />
          )}
          <YAxis
            domain={primaryDomain}
            tick={{ fill: 'var(--text)', fontSize: 11 }}
            tickFormatter={(value: number) => (metric === 'kwh' ? `${roundTick(value)}` : `£${roundTick(value)}`)}
            width={56}
          />
          {(solar?.useSecondaryAxis || load?.useSecondaryAxis) && (
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
              // The default content, adjusted by hand: zero happy-hour entries are dropped, and
              // with a 5-minute line (Recharts can't supply that series' entry - it sits on a
              // separate axis with a different point count, see fineSeries) the hovered bar's
              // half-hour average for it is appended.
              content={(props) => (
                <DefaultTooltipContent
                  {...props}
                  payload={withFineAverages(withoutZeroHappyHour(props.payload), props.label, fineAverages)}
                />
              )}
              formatter={(value, name) => {
                if (solar && (name === solarSeriesName || name === SOLAR_AVG_SERIES_NAME)) {
                  return [formatSolarValue(Number(value), solar.unit), name]
                }
                if (battery && (name === BATTERY_SERIES_NAME || name === BATTERY_AVG_SERIES_NAME)) {
                  return [formatBatteryValue(Number(value)), name]
                }
                if (load && (name === LOAD_SERIES_NAME || name === LOAD_AVG_SERIES_NAME)) {
                  return [formatLoadValue(Number(value)), name]
                }
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
                {/* Last, so happy-hour usage stacks on top of peak/off-peak. Same key pattern as
                    the bars above so it remounts with them and keeps its position. */}
                {happyHourMpans.has(mp.mpan) && (
                  <Bar
                    key={`${mp.mpan}-happyhour-${metric}-${hasSplit}`}
                    dataKey={happyHourKey(mp.mpan)}
                    stackId={stackIdFor(mp)}
                    fill="var(--chart-happy-hour)"
                    name={`${label} – Happy hour`}
                  />
                )}
              </Fragment>
            )
          })}
          {/* The 5-minute lines (Day page): each is drawn from its own 288 points against the shared
              hidden time axis. Their tooltip entries and hover markers are switched off - both
              index into the bars' 48 rows, so they'd land on the wrong point of these series; the
              tooltip gets each one's half-hour average from the Tooltip's content instead. Their
              dataKeys deliberately differ from the half-hour series' below, since Recharts drops
              tooltip entries that share a dataKey. */}
          {solar?.fineSeries && (
            <Area
              {...overlayAreaProps('var(--chart-solar)')}
              xAxisId={FINE_X_AXIS_ID}
              yAxisId={solar.useSecondaryAxis ? 'solar' : undefined}
              data={fineLineData(solar.fineSeries, 'solarFineValue')}
              dataKey="solarFineValue"
              name={solarSeriesName}
              tooltipType="none"
              activeDot={false}
            />
          )}
          {battery?.fineSeries && (
            <Area
              {...overlayAreaProps('var(--chart-battery)')}
              xAxisId={FINE_X_AXIS_ID}
              yAxisId="battery"
              data={fineLineData(battery.fineSeries, 'batteryFineValue')}
              dataKey="batteryFineValue"
              name={BATTERY_SERIES_NAME}
              tooltipType="none"
              activeDot={false}
            />
          )}
          {load?.fineSeries && (
            <Area
              {...overlayAreaProps('var(--chart-load)')}
              xAxisId={FINE_X_AXIS_ID}
              yAxisId={load.useSecondaryAxis ? 'solar' : undefined}
              data={fineLineData(load.fineSeries, 'loadFineValue')}
              dataKey="loadFineValue"
              name={LOAD_SERIES_NAME}
              tooltipType="none"
              activeDot={false}
            />
          )}
          {solar && !solar.fineSeries && (
            <Area
              {...overlayAreaProps('var(--chart-solar)')}
              yAxisId={solar.useSecondaryAxis ? 'solar' : undefined}
              dataKey="solarValue"
              name={solarSeriesName}
            />
          )}
          {battery && !battery.fineSeries && (
            <Area
              {...overlayAreaProps('var(--chart-battery)')}
              yAxisId="battery"
              dataKey="batteryValue"
              name={BATTERY_SERIES_NAME}
            />
          )}
          {load && !load.fineSeries && (
            <Area
              {...overlayAreaProps('var(--chart-load)')}
              yAxisId={load.useSecondaryAxis ? 'solar' : undefined}
              dataKey="loadValue"
              name={LOAD_SERIES_NAME}
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
        {/* One entry for both the bars' happy-hour segment and, on the Day page, the shaded band
            behind them - they share the colour. */}
        {(happyHourMpans.size > 0 || happyHourRanges.length > 0) && (
          <span className="usage-bar-chart__legend-entry">
            <span className="usage-bar-chart__legend-swatch" style={{ background: 'var(--chart-happy-hour)' }} />
            Happy Hour
          </span>
        )}
      </div>
    </div>
  )
}
