import type { SolarPowerPoint } from '../api/types'

export const SLOT_MINUTES = 5
export const MINUTES_PER_DAY = 24 * 60
export const SLOTS_PER_DAY = MINUTES_PER_DAY / SLOT_MINUTES
const WATTS_PER_KW = 1000
// "yyyy-MM-dd HH:mm:ss" - captures the hour and minute.
const TIME_PATTERN = /^\d{4}-\d{2}-\d{2} (\d{2}):(\d{2}):\d{2}$/

export interface SlotPoint {
  // Minutes after midnight that this 5-minute slot starts at - a number rather than an "HH:mm"
  // label so the x-axis can be a true numeric scale spanning the whole day, not just the slots
  // that happen to have data.
  minutes: number
  // In whatever unit the series it belongs to is in (kW for solar/load, percent for battery).
  value: number | null
}

// One entry for every 5-minute slot of the day (288 of them), so the chart's x-axis always covers
// midnight to midnight regardless of how much of the day has happened yet. A slot with no reading
// - the rest of today still to come, or a reading Growatt skipped - is null, which the chart
// draws as a gap rather than a fabricated zero. Growatt reports roughly every 5 minutes but not
// on exact slot boundaries, so a slot's value is the average of whatever readings fall inside it.
// Point times are "yyyy-MM-dd HH:mm:ss" in Growatt's own local time (see SolarPowerPoint). `pick`
// selects which field of a point to bucket (a null value is skipped) and `divisor` converts it to
// the unit the caller wants (Watts -> kW; percent needs none).
export function bucketToFiveMinuteSlots(
  points: SolarPowerPoint[],
  pick: (p: SolarPowerPoint) => number | null,
  divisor: number,
): SlotPoint[] {
  const sums = new Array<{ total: number; count: number } | undefined>(SLOTS_PER_DAY)
  for (const p of points) {
    const reading = pick(p)
    if (reading === null) continue
    // Strict match rather than slicing fixed offsets: Number('') is 0, so a malformed time would
    // otherwise silently land in the midnight slot instead of being skipped.
    const match = TIME_PATTERN.exec(p.time)
    if (!match) continue
    const slot = Math.floor((Number(match[1]) * 60 + Number(match[2])) / SLOT_MINUTES)
    if (slot < 0 || slot >= SLOTS_PER_DAY) continue
    const entry = sums[slot] ?? { total: 0, count: 0 }
    entry.total += reading
    entry.count += 1
    sums[slot] = entry
  }
  return Array.from({ length: SLOTS_PER_DAY }, (_, slot) => {
    const entry = sums[slot]
    return {
      minutes: slot * SLOT_MINUTES,
      value: entry ? entry.total / entry.count / divisor : null,
    }
  })
}

export function solarKwSlots(points: SolarPowerPoint[]): SlotPoint[] {
  return bucketToFiveMinuteSlots(points, (p) => p.powerWatts, WATTS_PER_KW)
}

export function batteryPercentSlots(points: SolarPowerPoint[]): SlotPoint[] {
  return bucketToFiveMinuteSlots(points, (p) => p.batteryPercent, 1)
}

export function loadKwSlots(points: SolarPowerPoint[]): SlotPoint[] {
  return bucketToFiveMinuteSlots(points, (p) => p.loadWatts, WATTS_PER_KW)
}
