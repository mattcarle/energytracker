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
import type { PeriodRow } from '../pages/usageShared'
import './UsageBarChart.css'

export type NetChartMetric = 'kwh' | 'cost'

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
    for (const mp of meterPoints) {
      const figures = row.byMpan[mp.mpan]
      if (!figures) continue
      value += metric === 'kwh' ? figures.kwh : figures.total
    }
    return { dayLabel: row.label, value }
  })

  // Thin out x-axis labels for a full month so they don't overlap; every day is still a
  // separate bar, only the tick labels are skipped. Mobile gets a much lower cap since the
  // same label count that fits a desktop-width chart collides at phone width.
  const maxLabels = isMobile ? 6 : 15
  const tickInterval = data.length > maxLabels ? Math.ceil(data.length / maxLabels) - 1 : 0

  return (
    <div className="usage-bar-chart">
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
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
          <Bar dataKey="value" fill="var(--accent)" name={metric === 'kwh' ? 'Net usage' : 'Net cost'} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
