import { useMemo, useState } from 'react'
import { getUsageByMonth } from '../api/client'
import UsagePeriodView, { type PeriodColumn } from './UsagePeriodView'
import {
  MONTH_NAMES,
  MONTH_NAMES_SHORT,
  fetchSolarMonthItems,
  pad2,
  useMeterPoints,
  useSolarPeriodOverlay,
  useUsagePeriodData,
  yearOptions,
  type RawPeriodItem,
  type UsagePeriodConfig,
} from './usageShared'

function monthKeysForYear(year: number): string[] {
  return Array.from({ length: 12 }, (_, i) => `${year}-${pad2(i + 1)}-01`)
}

function monthLabel(key: string): string {
  const [year, month] = key.split('-')
  return `${MONTH_NAMES[Number(month) - 1].slice(0, 3)} ${year}`
}

// The chart's x-axis has one tick per month within a single year already named in the page's
// own heading/controls, so repeating that year on every tick (see monthLabel, used by the
// table) would just be cramped and redundant there.
function monthChartLabel(key: string): string {
  const [, month] = key.split('-')
  return MONTH_NAMES_SHORT[Number(month) - 1]
}

function bucketKeyForMonth(dateStr: string): string[] {
  const [year, month] = dateStr.split('-')
  return [`${year}-${month}-01`]
}

async function fetchMonthItems(mpan: string, fromDate: string, toDate: string): Promise<RawPeriodItem[]> {
  const response = await getUsageByMonth(mpan, fromDate, toDate)
  return response.months.map((m) => ({
    key: m.usageMonth,
    isExport: m.isExport,
    kwh: m.kwh,
    cost: m.cost,
    avgRate: m.avgRate,
    kwhOffPeak: m.kwhOffPeak,
    costOffPeak: m.costOffPeak,
    intervalCount: m.intervalCount,
    missingIntervalCount: m.missingIntervalCount,
  }))
}

const PERIOD_COLUMNS: PeriodColumn[] = [{ header: 'Month', render: (row) => row.label }]

export default function UsageByYear() {
  const [year, setYear] = useState(new Date().getFullYear())
  const { meterPoints, error: meterPointsError } = useMeterPoints()

  const config = useMemo<UsagePeriodConfig>(
    () => ({
      fromDate: `${year}-01-01`,
      toDate: `${year + 1}-01-01`,
      expectedKeys: monthKeysForYear(year),
      labelForKey: monthLabel,
      chartLabelForKey: monthChartLabel,
      bucketKeyForStandingChargeDate: bucketKeyForMonth,
      fetchUsageItems: fetchMonthItems,
    }),
    [year],
  )

  const { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, stdChgDaysByMpan, error } = useUsagePeriodData(meterPoints, config)
  const solar = useSolarPeriodOverlay(`${year}-01-01`, `${year + 1}-01-01`, fetchSolarMonthItems)

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
          &lt;
        </button>
        <button type="button" onClick={() => setYear((y) => y + 1)} aria-label="Next year">
          &gt;
        </button>
      </div>
    </>
  )

  return (
    <UsagePeriodView
      title="Usage by Year"
      periodColumns={PERIOD_COLUMNS}
      averageRowLabel="AVG / MONTH"
      controls={controls}
      rows={rows}
      meterPoints={meterPoints}
      offPeakAvailableByMpan={offPeakAvailableByMpan}
      latestPeriodKeyByMpan={latestPeriodKeyByMpan}
      stdChgDaysByMpan={stdChgDaysByMpan}
      error={meterPointsError ?? error}
      noDataMessage={`No usage data for ${year}.`}
      enableInsights
      insightsPeriodLabel="Month"
      periodSummaryLabel={`${year}`}
      solarByKey={solar.byKey}
      solarTotalKwh={solar.totalKwh}
      solarAvailable={solar.available}
      solarUnit="kWh"
    />
  )
}
