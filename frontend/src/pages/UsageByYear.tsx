import { useMemo } from 'react'
import { getUsageByYear } from '../api/client'
import UsagePeriodView, { type PeriodColumn } from './UsagePeriodView'
import { pad2, useMeterPoints, useUsagePeriodData, type RawPeriodItem, type UsagePeriodConfig } from './usageShared'

// No period selection for this view - fetch across a wide-enough range to catch all historical
// data and let the backend's grouping decide which years actually appear.
const FROM_DATE = '2000-01-01'

function tomorrowIso(): string {
  const now = new Date()
  now.setDate(now.getDate() + 1)
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}

function bucketKeyForYear(dateStr: string): string[] {
  return [`${dateStr.slice(0, 4)}-01-01`]
}

async function fetchYearItems(mpan: string, fromDate: string, toDate: string): Promise<RawPeriodItem[]> {
  const response = await getUsageByYear(mpan, fromDate, toDate)
  return response.years.map((y) => ({
    key: y.usageYear,
    isExport: y.isExport,
    kwh: y.kwh,
    cost: y.cost,
    avgRate: y.avgRate,
    kwhOffPeak: y.kwhOffPeak,
    costOffPeak: y.costOffPeak,
  }))
}

const PERIOD_COLUMNS: PeriodColumn[] = [{ header: 'Year', render: (row) => row.key.slice(0, 4) }]

export default function UsageByYear() {
  const { meterPoints, error: meterPointsError } = useMeterPoints()

  // Unlike the other views, expectedKeys is left empty: "all years with no data" isn't a
  // bounded, meaningful list the way "all days in this month" is, so rows come purely from
  // whatever years actually have usage or a known standing charge.
  const config = useMemo<UsagePeriodConfig>(
    () => ({
      fromDate: FROM_DATE,
      toDate: tomorrowIso(),
      expectedKeys: [],
      labelForKey: (key) => key.slice(0, 4),
      bucketKeyForStandingChargeDate: bucketKeyForYear,
      fetchUsageItems: fetchYearItems,
    }),
    [],
  )

  const { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, error } = useUsagePeriodData(meterPoints, config)

  return (
    <UsagePeriodView
      title="Usage by year"
      periodColumns={PERIOD_COLUMNS}
      averageRowLabel="AVG / YEAR"
      rows={rows}
      meterPoints={meterPoints}
      offPeakAvailableByMpan={offPeakAvailableByMpan}
      latestPeriodKeyByMpan={latestPeriodKeyByMpan}
      error={meterPointsError ?? error}
      noDataMessage="No usage data available."
    />
  )
}
