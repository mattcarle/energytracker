import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { SolarPowerPoint } from '../api/types'
import { useIsMobile } from '../hooks/useIsMobile'
import { MINUTES_PER_DAY, solarKwSlots } from './solarTodaySlots'

// Every 3 hours on desktop, every 6 on a phone-width chart where 9 labels wouldn't fit. The last
// tick (24:00) closes the day so the axis visibly spans midnight to midnight.
const DESKTOP_TICKS = [0, 180, 360, 540, 720, 900, 1080, 1260, 1440]
const MOBILE_TICKS = [0, 360, 720, 1080, 1440]

function formatClock(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
}

export default function SolarTodayChart({ points }: { points: SolarPowerPoint[] }) {
  const isMobile = useIsMobile()
  const data = solarKwSlots(points)

  return (
    <ResponsiveContainer width="100%" height={210}>
      <AreaChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
        <XAxis
          type="number"
          dataKey="minutes"
          domain={[0, MINUTES_PER_DAY]}
          ticks={isMobile ? MOBILE_TICKS : DESKTOP_TICKS}
          tickFormatter={formatClock}
          tick={{ fill: 'var(--text)', fontSize: 11 }}
        />
        <YAxis
          domain={[0, 'auto']}
          tick={{ fill: 'var(--text)', fontSize: 11 }}
          tickFormatter={(value: number) => `${Math.round(value * 10) / 10}`}
          width={40}
        />
        {/* Dropped on mobile for the same reason as UsageBarChart's - the tooltip box can cover
            most of a phone-width chart under the finger that triggered it. */}
        {!isMobile && (
          <Tooltip
            formatter={(value) => [`${Number(value).toFixed(2)} kW`, 'Solar']}
            labelFormatter={(label) => formatClock(Number(label))}
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
        {/* No animation: the whole curve is redrawn on every 5-minute poll, and re-animating it
            from scratch each time would be distracting. */}
        <Area
          type="monotone"
          dataKey="value"
          name="Solar"
          stroke="var(--chart-solar)"
          fill="var(--chart-solar)"
          fillOpacity={0.25}
          strokeWidth={2}
          dot={false}
          connectNulls={false}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}
