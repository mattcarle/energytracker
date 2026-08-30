import { useEffect, useMemo, useRef, useState } from 'react'
import { getUsageByHalfHour } from '../api/client'
import UsagePeriodView, { type PeriodColumn } from './UsagePeriodView'
import {
  addDays,
  formatFullDate,
  pad2,
  useMeterPoints,
  useSolarDayOverlay,
  useUsagePeriodData,
  type RawPeriodItem,
  type UsagePeriodConfig,
} from './usageShared'

function halfHourKeys(): string[] {
  const keys: string[] = []
  for (let h = 0; h < 24; h++) {
    for (const m of [0, 30]) {
      keys.push(`${pad2(h)}:${pad2(m)}`)
    }
  }
  return keys
}

const EXPECTED_KEYS = halfHourKeys()

async function fetchHalfHourItems(mpan: string, fromDate: string, toDate: string): Promise<RawPeriodItem[]> {
  const response = await getUsageByHalfHour(mpan, fromDate, toDate)
  return response.halfHours.map((h) => ({
    // usageInterval is a LocalDateTime like "2026-08-10T14:30:00" - the HH:mm portion is the
    // period key since only one day is ever in range for this view.
    key: h.usageInterval.slice(11, 16),
    isExport: h.isExport,
    kwh: h.kwh,
    cost: h.cost,
    avgRate: h.avgRate,
    kwhOffPeak: h.kwhOffPeak,
    costOffPeak: h.costOffPeak,
    intervalCount: h.intervalCount,
    missingIntervalCount: h.missingIntervalCount,
  }))
}

const PERIOD_COLUMNS: PeriodColumn[] = [{ header: 'Time', render: (row) => row.label }]

function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}

// A "real" reading for a half-hour has intervalCount > 0 (a row actually came back for that
// key) and missingIntervalCount === 0 (it wasn't a data-integrity-check placeholder standing in
// for a half-hour Octopus never reported) - see MpanFigures. A key with no entry at all in
// byMpan (Octopus hasn't published it yet) counts the same as one flagged missing: either way,
// the day isn't complete yet.
function isRealHalfHour(figures: { intervalCount: number; missingIntervalCount: number } | undefined): boolean {
  return figures !== undefined && figures.intervalCount > 0 && figures.missingIntervalCount === 0
}

// Every meter point needs a real reading for every one of the day's 48 half-hour periods -
// checked per mpan (not "any mpan") so a lagging meter (see the per-mpan resume-point logic in
// OctopusService.loadUsageData) still causes the default view to step back, rather than landing
// on a day where only some meters are complete.
function hasFullDayOfData(
  rows: { key: string; byMpan: Record<string, { intervalCount: number; missingIntervalCount: number }> }[],
  meterPoints: { mpan: string }[] | null,
): boolean {
  if (!meterPoints || meterPoints.length === 0) return false
  const rowByKey = new Map(rows.map((row) => [row.key, row]))
  return meterPoints.every((mp) =>
    EXPECTED_KEYS.every((key) => isRealHalfHour(rowByKey.get(key)?.byMpan[mp.mpan])),
  )
}

// Different meters on the same account can lag by a different number of days, so there's no
// single "latest" timestamp to trust - instead this walks backward from today one day at a time
// until it lands on a day that actually has a full set of half-hour data, which self-corrects
// regardless of which meter is behind. Capped so an account with no data at all doesn't walk
// back forever.
const MAX_AUTO_STEPS = 60

export default function UsageByDay() {
  const [date, setDate] = useState(todayIso())
  const [dateChangedByUser, setDateChangedByUser] = useState(false)
  const [autoStepping, setAutoStepping] = useState(true)
  const autoStepCount = useRef(0)
  const { meterPoints, error: meterPointsError } = useMeterPoints()

  const config = useMemo<UsagePeriodConfig>(
    () => ({
      fromDate: date,
      toDate: addDays(date, 1),
      expectedKeys: EXPECTED_KEYS,
      labelForKey: (key) => key,
      // Only one day is ever in range for this view, so its standing charge is split evenly
      // across all of that day's half-hour periods rather than attached to a single one.
      bucketKeyForStandingChargeDate: () => EXPECTED_KEYS,
      fetchUsageItems: fetchHalfHourItems,
    }),
    [date],
  )

  const { rows, offPeakAvailableByMpan, latestPeriodKeyByMpan, stdChgDaysByMpan, error } = useUsagePeriodData(meterPoints, config)
  const solar = useSolarDayOverlay(date)

  useEffect(() => {
    if (dateChangedByUser) {
      setAutoStepping(false)
      return
    }
    if (!rows) return
    if (hasFullDayOfData(rows, meterPoints)) {
      setAutoStepping(false)
      return
    }
    if (autoStepCount.current >= MAX_AUTO_STEPS) {
      setAutoStepping(false)
      return
    }
    autoStepCount.current += 1
    setDate((d) => addDays(d, -1))
  }, [rows, meterPoints, dateChangedByUser])

  // While still auto-stepping backward looking for a day with data, show "loading" rather than
  // a series of "no usage data for X" flashes for each empty day it passes through.
  const displayRows = autoStepping && !dateChangedByUser ? null : rows

  function goToPreviousDay() {
    setDateChangedByUser(true)
    setDate((d) => addDays(d, -1))
  }

  function goToNextDay() {
    setDateChangedByUser(true)
    setDate((d) => addDays(d, 1))
  }

  const controls = (
    <>
      <label>
        Date
        <input
          type="date"
          value={date}
          onChange={(e) => {
            setDateChangedByUser(true)
            setDate(e.target.value)
          }}
        />
      </label>
      <div className="usage-page__month-nav">
        <button type="button" onClick={goToPreviousDay} aria-label="Previous day">
          &lt;
        </button>
        <button type="button" onClick={goToNextDay} aria-label="Next day">
          &gt;
        </button>
      </div>
    </>
  )

  return (
    <UsagePeriodView
      title="Usage by Day"
      periodColumns={PERIOD_COLUMNS}
      averageRowLabel="AVG / HALF-HR"
      controls={controls}
      rows={displayRows}
      meterPoints={meterPoints}
      offPeakAvailableByMpan={offPeakAvailableByMpan}
      latestPeriodKeyByMpan={latestPeriodKeyByMpan}
      stdChgDaysByMpan={stdChgDaysByMpan}
      error={meterPointsError ?? error}
      noDataMessage={`No usage data for ${date}.`}
      enableInsights
      insightsPeriodLabel="Half Hour"
      periodSummaryLabel={formatFullDate(date)}
      solarByKey={solar.byKey}
      solarTotalKwh={solar.totalKwh}
      solarAvailable={solar.available}
      solarUnit="kW"
      batteryByKey={solar.batteryByKey}
      batteryAvailable={solar.available}
      loadByKey={solar.loadByKey}
      loadAvailable={solar.available}
    />
  )
}
