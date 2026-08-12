import { useMemo, useState } from 'react'
import { getUsageByWeek } from '../api/client'
import UsagePeriodView, { type PeriodColumn } from './UsagePeriodView'
import {
  addDays,
  formatDayLabelWithYear,
  isoWeekMonday,
  useMeterPoints,
  useUsagePeriodData,
  yearOptions,
  type RawPeriodItem,
  type UsagePeriodConfig,
} from './usageShared'

// All ISO weeks (Monday start) whose Monday falls within the given calendar year - the first
// week can start in late December of the previous year (matching H2's DATE_TRUNC('WEEK', ...)
// on the backend), so the row set lines up with whatever the backend actually returns.
function weekKeysForYear(year: number): string[] {
  const keys: string[] = []
  let current = isoWeekMonday(`${year}-01-01`)
  const boundary = `${year}-12-31`
  while (current <= boundary) {
    keys.push(current)
    current = addDays(current, 7)
  }
  return keys
}

async function fetchWeekItems(mpan: string, fromDate: string, toDate: string): Promise<RawPeriodItem[]> {
  const response = await getUsageByWeek(mpan, fromDate, toDate)
  return response.weeks.map((w) => ({
    key: w.usageWeek,
    isExport: w.isExport,
    kwh: w.kwh,
    cost: w.cost,
    avgRate: w.avgRate,
    kwhOffPeak: w.kwhOffPeak,
    costOffPeak: w.costOffPeak,
  }))
}

const PERIOD_COLUMNS: PeriodColumn[] = [{ header: 'Week starting', render: (row) => row.label }]

export default function UsageByWeek() {
  const [year, setYear] = useState(new Date().getFullYear())
  const { meterPoints, error: meterPointsError } = useMeterPoints()

  const config = useMemo<UsagePeriodConfig>(
    () => ({
      fromDate: `${year}-01-01`,
      toDate: `${year + 1}-01-01`,
      expectedKeys: weekKeysForYear(year),
      labelForKey: formatDayLabelWithYear,
      bucketKeyForStandingChargeDate: (dateStr) => [isoWeekMonday(dateStr)],
      fetchUsageItems: fetchWeekItems,
    }),
    [year],
  )

  const { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, error } = useUsagePeriodData(meterPoints, config)

  const controls = (
    <>
      <label>
        Year
        <select value={year} onChange={(e) => setYear(Number(e.target.value))}>
          {yearOptions(year).map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
      </label>
      <div className="usage-page__month-nav">
        <button type="button" onClick={() => setYear((y) => y - 1)} aria-label="Previous year">
          &larr;
        </button>
        <button type="button" onClick={() => setYear((y) => y + 1)} aria-label="Next year">
          &rarr;
        </button>
      </div>
    </>
  )

  return (
    <UsagePeriodView
      title="Usage by week"
      periodColumns={PERIOD_COLUMNS}
      averageRowLabel="AVG / WEEK"
      controls={controls}
      rows={rows}
      meterPoints={meterPoints}
      offPeakAvailableByMpan={offPeakAvailableByMpan}
      latestPeriodKeyByMpan={latestPeriodKeyByMpan}
      error={meterPointsError ?? error}
      noDataMessage={`No usage data for ${year}.`}
    />
  )
}
