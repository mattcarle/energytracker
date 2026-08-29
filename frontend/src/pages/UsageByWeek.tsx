import { useMemo, useState } from 'react'
import { getUsageByDay } from '../api/client'
import UsagePeriodView, { type PeriodColumn } from './UsagePeriodView'
import {
  addDays,
  dayOfWeek,
  fetchSolarDayItems,
  formatDayLabel,
  formatDayLabelWithYear,
  isoWeekMonday,
  pad2,
  useMeterPoints,
  useSolarPeriodOverlay,
  useUsagePeriodData,
  type RawPeriodItem,
  type UsagePeriodConfig,
} from './usageShared'

function weekKeys(weekStart: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))
}

async function fetchDayItems(mpan: string, fromDate: string, toDate: string): Promise<RawPeriodItem[]> {
  const response = await getUsageByDay(mpan, fromDate, toDate)
  return response.days.map((d) => ({
    key: d.usageDate,
    isExport: d.isExport,
    kwh: d.kwh,
    cost: d.cost,
    avgRate: d.avgRate,
    kwhOffPeak: d.kwhOffPeak,
    costOffPeak: d.costOffPeak,
    intervalCount: d.intervalCount,
    missingIntervalCount: d.missingIntervalCount,
  }))
}

const PERIOD_COLUMNS: PeriodColumn[] = [
  { header: 'Day', render: (row) => dayOfWeek(row.key) },
  { header: 'Date', render: (row) => row.label },
]

function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}

export default function UsageByWeek() {
  const [weekStart, setWeekStart] = useState(() => isoWeekMonday(todayIso()))
  const { meterPoints, error: meterPointsError } = useMeterPoints()

  const config = useMemo<UsagePeriodConfig>(
    () => ({
      fromDate: weekStart,
      toDate: addDays(weekStart, 7),
      expectedKeys: weekKeys(weekStart),
      labelForKey: formatDayLabel,
      // The table already has a separate "Day" column for this - the chart's x-axis has no such
      // column, so it shows the weekday itself rather than the date (see UsageByYear for the
      // same idea: the table stays detailed, the chart's one line per tick stays short).
      chartLabelForKey: dayOfWeek,
      bucketKeyForStandingChargeDate: (dateStr) => [dateStr],
      fetchUsageItems: fetchDayItems,
    }),
    [weekStart],
  )

  const { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, stdChgDaysByMpan, error } = useUsagePeriodData(meterPoints, config)
  const solar = useSolarPeriodOverlay(weekStart, addDays(weekStart, 7), fetchSolarDayItems)

  function goToPreviousWeek() {
    setWeekStart((d) => addDays(d, -7))
  }

  function goToNextWeek() {
    setWeekStart((d) => addDays(d, 7))
  }

  const weekEnd = addDays(weekStart, 6)

  const controls = (
    <>
      <label>
        Week starting
        <input type="date" value={weekStart} onChange={(e) => setWeekStart(isoWeekMonday(e.target.value))} />
      </label>
      <div className="usage-page__month-nav">
        <button type="button" onClick={goToPreviousWeek} aria-label="Previous week">
          &lt;
        </button>
        <button type="button" onClick={goToNextWeek} aria-label="Next week">
          &gt;
        </button>
      </div>
    </>
  )

  return (
    <UsagePeriodView
      title="Usage by Week"
      periodColumns={PERIOD_COLUMNS}
      averageRowLabel="AVG / DAY"
      controls={controls}
      rows={rows}
      meterPoints={meterPoints}
      offPeakAvailableByMpan={offPeakAvailableByMpan}
      latestPeriodKeyByMpan={latestPeriodKeyByMpan}
      stdChgDaysByMpan={stdChgDaysByMpan}
      error={meterPointsError ?? error}
      noDataMessage={`No usage data for the week of ${formatDayLabelWithYear(weekStart)}.`}
      enableInsights
      insightsPeriodLabel="Day"
      periodSummaryLabel={`${formatDayLabel(weekStart)} – ${formatDayLabelWithYear(weekEnd)}`}
      solarByKey={solar.byKey}
      solarTotalKwh={solar.totalKwh}
      solarAvailable={solar.available}
      solarUnit="kWh"
    />
  )
}
