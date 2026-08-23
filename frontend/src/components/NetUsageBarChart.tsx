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
import type { PeriodRow } from '../pages/usageShared'
import './UsageBarChart.css'

export type NetChartMetric = 'kwh' | 'cost'

// Same idea as UsageBarChart's per-series hatch: a period missing if any included MPAN
// contributed a data-integrity-check placeholder interval (matching how the table's TOTAL
// column also flags "any", not "all") is drawn in the bar's own accent color, textured instead
// of flat-filled, rather than switching to a different "warning" color.
const MISSING_DATA_HATCH_ID = 'net-usage-bar-chart-missing-hatch'
const MISSING_DATA_FILL = `url(#${MISSING_DATA_HATCH_ID})`

// A fully missing period nets to exactly 0 and would otherwise render as an invisible
// zero-height bar - minPointSize gives it a visible sliver instead. See UsageBarChart's
// identical constant for why this doesn't touch a genuine zero-value bar.
const MIN_MISSING_BAR_PX = 3

interface NetUsageBarChartProps {
  rows: PeriodRow[]
  meterPoints: MeterPoint[]
  metric: NetChartMetric
}

function formatValue(value: number, metric: NetChartMetric): string {
  return metric === 'kwh' ? `${value.toFixed(2)} kWh` : `£${value.toFixed(2)}`
}

// The net figure per period - kwh/total summed across the included MPANs, with export already
// signed negative upstream - the same value the table's TOTAL columns show.
export default function NetUsageBarChart({ rows, meterPoints, metric }: NetUsageBarChartProps) {
  const isMobile = useIsMobile()
  const data = rows.map((row) => {
    let value = 0
    let missing = false
    // Fully missing only when every included MPAN that had any interval that period is
    // entirely placeholder data - a legitimate net-zero day (e.g. import cancelled by export)
    // must not be flagged, or it would grow a minimal bar it doesn't need.
    let hadAnyIntervals = false
    let fullyMissing = true
    for (const mp of meterPoints) {
      const figures = row.byMpan[mp.mpan]
      if (!figures) continue
      value += metric === 'kwh' ? figures.kwh : figures.total
      missing = missing || figures.missingIntervalCount > 0
      if (figures.intervalCount > 0) {
        hadAnyIntervals = true
        if (figures.missingIntervalCount !== figures.intervalCount) fullyMissing = false
      }
    }
    return { dayLabel: row.label, value, missing, fullyMissing: hadAnyIntervals && fullyMissing && missing }
  })

  // Thin out x-axis labels for a full month so they don't overlap; every day is still a
  // separate bar, only the tick labels are skipped. Mobile gets a much lower cap since the
  // same label count that fits a desktop-width chart collides at phone width.
  const maxLabels = isMobile ? 6 : 15
  const tickInterval = data.length > maxLabels ? Math.ceil(data.length / maxLabels) - 1 : 0

  const hasAnyMissing = data.some((point) => point.missing)

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
          <defs>
            <pattern
              id={MISSING_DATA_HATCH_ID}
              width="6"
              height="6"
              patternUnits="userSpaceOnUse"
              patternTransform="rotate(45)"
            >
              <rect width="6" height="6" fill="var(--accent)" opacity="0.25" />
              <line x1="0" y1="0" x2="0" y2="6" stroke="var(--accent)" strokeWidth="1.5" />
              <line x1="0" y1="0" x2="6" y2="0" stroke="var(--accent)" strokeWidth="1.5" />
            </pattern>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis dataKey="dayLabel" tick={{ fill: 'var(--text)', fontSize: 11 }} interval={tickInterval} />
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
              formatter={(value) => [formatValue(Number(value), metric), metric === 'kwh' ? 'Net usage' : 'Net cost']}
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
          <Bar
            dataKey="value"
            fill="var(--accent)"
            name={metric === 'kwh' ? 'Net usage' : 'Net cost'}
            minPointSize={(_value, i) => (data[i]?.fullyMissing ? MIN_MISSING_BAR_PX : 0)}
          >
            {data.map((entry, i) => (
              <Cell key={i} fill={entry.missing ? MISSING_DATA_FILL : 'var(--accent)'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      {hasAnyMissing && (
        <div className="usage-bar-chart__legend">
          <span className="usage-bar-chart__legend-entry">
            <span className="usage-bar-chart__legend-swatch usage-bar-chart__legend-swatch--missing" />
            Missing data
          </span>
        </div>
      )}
    </div>
  )
}
