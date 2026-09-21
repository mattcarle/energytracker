// Octopus usage lags well behind real time (typically more than a day), so a Day view of today -
// or of the last day Octopus has reported on - is usually only partly covered. This works out
// whether the shown day is fully covered and, if not, the warning to show.

const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// The API's LocalDateTimes ("2026-09-20T23:30:00", naive local time) -> "20 Sep 2026 23:30".
export function formatDataEnd(dateTime: string): string {
  const [datePart, timePart = ''] = dateTime.split('T')
  const [year, month, day] = datePart.split('-')
  return `${Number(day)} ${MONTHS_SHORT[Number(month) - 1]} ${year} ${timePart.slice(0, 5)}`
}

// "YYYY-MM-DD" -> the following day, same format (UTC arithmetic so DST can't shift the date).
function nextDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day + 1)).toISOString().slice(0, 10)
}

// `latestByMpan` is each meter point's latest usage interval END (UsageDateRange.latest): usage
// up to that instant is available, nothing after it. `selectedMpans` are the meter points on
// screen - a lagging meter the user has switched off shouldn't warn about a chart that doesn't
// show it - and the most-lagging of them decides. Returns null when they all cover the whole of
// `date` (the shown day), or when none of them has any usage at all to be "behind" on.
export function octopusDataWarning(
  latestByMpan: ReadonlyMap<string, string>,
  selectedMpans: readonly string[],
  date: string,
): string | null {
  // Compared as "YYYY-MM-DDTHH:mm" strings - fixed width, so plain string order is time order.
  const dayEnd = `${nextDay(date)}T00:00`
  let mostLagging: string | null = null
  for (const mpan of selectedMpans) {
    const latest = latestByMpan.get(mpan)?.slice(0, 16)
    if (latest === undefined) continue
    if (mostLagging === null || latest < mostLagging) mostLagging = latest
  }
  if (mostLagging === null || mostLagging >= dayEnd) return null
  return `Octopus data after ${formatDataEnd(mostLagging)} not yet available`
}
