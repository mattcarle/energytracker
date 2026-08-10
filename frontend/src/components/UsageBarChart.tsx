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
import { formatDate, meterPointLabel, type DayRow } from '../pages/UsageByDay'
import './UsageBarChart.css'

const MPAN_COLORS = ['var(--chart-mpan-1)', 'var(--chart-mpan-2)', 'var(--chart-mpan-3)']

export type ChartMetric = 'kwh' | 'cost'

interface UsageBarChartProps {
  rows: DayRow[]
  meterPoints: MeterPoint[]
  metric: ChartMetric
}

function usageKey(mpan: string): string {
  return `${mpan}_usage`
}

function stdChgKey(mpan: string): string {
  return `${mpan}_stdChg`
}

function formatValue(value: number, metric: ChartMetric): string {
  return metric === 'kwh' ? `${value.toFixed(2)} kWh` : `£${value.toFixed(2)}`
}

export default function UsageBarChart({ rows, meterPoints, metric }: UsageBarChartProps) {
  const data = rows.map((row) => {
    const point: Record<string, number | string> = { dayLabel: formatDate(row.date) }
    for (const mp of meterPoints) {
      const figures = row.byMpan[mp.mpan]
      if (metric === 'cost') {
        point[stdChgKey(mp.mpan)] = figures ? figures.stdChg : 0
      }
      point[usageKey(mp.mpan)] = figures ? (metric === 'kwh' ? figures.kwh : figures.usageCost) : 0
    }
    return point
  })

  // Thin out x-axis labels for a full month so they don't overlap; every day is still a
  // separate bar group, only the tick labels are skipped.
  const tickInterval = data.length > 15 ? Math.ceil(data.length / 15) - 1 : 0

  // A hand-built legend rather than recharts' <Legend>: one entry per MPAN plus a single
  // shared "Standing charge" entry, instead of one duplicated per MPAN's stacked bar.
  const legendEntries = [
    ...meterPoints.map((mp, index) => ({
      label: `MPAN ${mp.mpan} (${meterPointLabel(mp)})`,
      color: MPAN_COLORS[index % MPAN_COLORS.length],
    })),
    ...(metric === 'cost' ? [{ label: 'Standing charge', color: 'var(--chart-stdchg)' }] : []),
  ]

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={360}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
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
          <Tooltip
            formatter={(value, name) => [formatValue(Number(value), metric), name]}
            contentStyle={{ background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text-h)' }}
            labelStyle={{ color: 'var(--text-h)' }}
          />
          {meterPoints.map((mp, index) => (
            <Fragment key={mp.mpan}>
              {/* Recharts tracks each stack segment's position by registration (mount) order,
                  not by <Bar> declaration order in a given render - the usage bar mounts once
                  and stays at its original position even after the standing-charge bar mounts
                  later. Keying both bars with `metric` forces them to remount together on every
                  toggle, so declaration order (standing charge first, against the axis) is
                  actually honoured each time. */}
              {metric === 'cost' && (
                <Bar
                  key={`${mp.mpan}-stdchg-${metric}`}
                  dataKey={stdChgKey(mp.mpan)}
                  stackId={mp.mpan}
                  fill="var(--chart-stdchg)"
                  name="Standing charge"
                />
              )}
              <Bar
                key={`${mp.mpan}-usage-${metric}`}
                dataKey={usageKey(mp.mpan)}
                stackId={mp.mpan}
                fill={MPAN_COLORS[index % MPAN_COLORS.length]}
                name={`MPAN ${mp.mpan} (${meterPointLabel(mp)})`}
              />
            </Fragment>
          ))}
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
