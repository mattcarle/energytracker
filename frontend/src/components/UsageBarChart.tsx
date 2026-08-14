import { Fragment } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
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

// Electricity import and export share a stackId so they render as one bar per day rather
// than two side-by-side ones. Combined with stackOffset="sign" on the BarChart below, values
// sharing a stackId diverge by sign from a common zero baseline - import's positive values
// stack upward, export's negative values stack downward, in the same bar. Gas keeps its own
// stackId per meter point, rendering as a separate bar alongside it.
function stackIdFor(mp: MeterPoint): string {
  return mp.meterType === 'ELEC' ? 'electricity' : mp.mpan
}

function formatValue(value: number, metric: ChartMetric): string {
  return metric === 'kwh' ? `${value.toFixed(2)} kWh` : `£${value.toFixed(2)}`
}

export default function UsageBarChart({ rows, meterPoints, metric, offPeakAvailableByMpan }: UsageBarChartProps) {
  const isMobile = useIsMobile()
  const data = rows.map((row) => {
    const point: Record<string, number | string> = { dayLabel: row.label }
    for (const mp of meterPoints) {
      const figures = row.byMpan[mp.mpan]
      const hasSplit = offPeakAvailableByMpan?.get(mp.mpan) ?? false
      if (metric === 'cost') {
        point[stdChgKey(mp.mpan)] = figures ? figures.stdChg : 0
      }
      if (hasSplit) {
        const total = figures ? (metric === 'kwh' ? figures.kwh : figures.usageCost) : 0
        const offPeak = figures ? (metric === 'kwh' ? figures.kwhOffPeak : figures.costOffPeak) : 0
        point[offPeakKey(mp.mpan)] = offPeak
        point[peakKey(mp.mpan)] = total - offPeak
      } else {
        point[usageKey(mp.mpan)] = figures ? (metric === 'kwh' ? figures.kwh : figures.usageCost) : 0
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

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={360}>
        <BarChart data={data} stackOffset="sign" margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
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
                    />
                    <Bar
                      key={`${mp.mpan}-peak-${metric}-${hasSplit}`}
                      dataKey={peakKey(mp.mpan)}
                      stackId={stackIdFor(mp)}
                      fill={MPAN_COLORS[index % MPAN_COLORS.length]}
                      name={`${label} – Peak`}
                    />
                  </>
                ) : (
                  <Bar
                    key={`${mp.mpan}-usage-${metric}-${hasSplit}`}
                    dataKey={usageKey(mp.mpan)}
                    stackId={stackIdFor(mp)}
                    fill={MPAN_COLORS[index % MPAN_COLORS.length]}
                    name={label}
                  />
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
      </div>
    </div>
  )
}
