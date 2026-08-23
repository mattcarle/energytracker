import { useMemo, useState } from 'react'
import { getUsageByDay } from '../api/client'
import UsagePeriodView, { type PeriodColumn } from './UsagePeriodView'
import {
  MONTH_NAMES,
  MONTH_NAMES_SHORT,
  dayOfWeek,
  pad2,
  useMeterPoints,
  useUsagePeriodData,
  yearOptions,
  type RawPeriodItem,
  type UsagePeriodConfig,
} from './usageShared'

function firstOfMonth(year: number, month: number): string {
  return `${year}-${pad2(month)}-01`
}

function firstOfNextMonth(year: number, month: number): string {
  return month === 12 ? `${year + 1}-01-01` : `${year}-${pad2(month + 1)}-01`
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
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

export default function UsageByDay() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  const { meterPoints, error: meterPointsError } = useMeterPoints()

  const config = useMemo<UsagePeriodConfig>(() => {
    const total = daysInMonth(year, month)
    const expectedKeys = Array.from({ length: total }, (_, i) => `${year}-${pad2(month)}-${pad2(i + 1)}`)
    return {
      fromDate: firstOfMonth(year, month),
      toDate: firstOfNextMonth(year, month),
      expectedKeys,
      labelForKey: (key) => {
        const [, monthPart, dayPart] = key.split('-')
        return `${dayPart}-${MONTH_NAMES[Number(monthPart) - 1].slice(0, 3)}`
      },
      bucketKeyForStandingChargeDate: (dateStr) => [dateStr],
      fetchUsageItems: fetchDayItems,
    }
  }, [year, month])

  const { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, stdChgDaysByMpan, error } = useUsagePeriodData(meterPoints, config)

  function goToPreviousMonth() {
    if (month === 1) {
      setYear((y) => y - 1)
      setMonth(12)
    } else {
      setMonth((m) => m - 1)
    }
  }

  function goToNextMonth() {
    if (month === 12) {
      setYear((y) => y + 1)
      setMonth(1)
    } else {
      setMonth((m) => m + 1)
    }
  }

  const controls = (
    <>
      <label>
        Month
        <select value={month} onChange={(e) => setMonth(Number(e.target.value))}>
          {MONTH_NAMES_SHORT.map((name, index) => (
            <option key={name} value={index + 1}>
              {name}
            </option>
          ))}
        </select>
      </label>
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
        <button type="button" onClick={goToPreviousMonth} aria-label="Previous month">
          &lt;
        </button>
        <button type="button" onClick={goToNextMonth} aria-label="Next month">
          &gt;
        </button>
      </div>
    </>
  )

  return (
    <UsagePeriodView
      title="Usage by day"
      periodColumns={PERIOD_COLUMNS}
      averageRowLabel="AVG / DAY"
      controls={controls}
      rows={rows}
      meterPoints={meterPoints}
      offPeakAvailableByMpan={offPeakAvailableByMpan}
      latestPeriodKeyByMpan={latestPeriodKeyByMpan}
      stdChgDaysByMpan={stdChgDaysByMpan}
      error={meterPointsError ?? error}
      noDataMessage={`No usage data for ${MONTH_NAMES[month - 1]} ${year}.`}
      enableInsights
      insightsPeriodLabel="Day"
      periodSummaryLabel={`${MONTH_NAMES_SHORT[month - 1]} ${year}`}
    />
  )
}
