import { useEffect, useState } from 'react'
import { getSolarLive } from '../api/client'
import type { SolarLiveStatus } from '../api/types'
import SolarTodayChart from '../components/SolarTodayChart'
import { formatKwh, solarIsAvailable } from './usageShared'
import './Live.css'

// Growatt reports roughly every 5 minutes (the same cadence the Day page's intraday curve is
// bucketed to - see usageShared's bucketToHalfHours), so polling faster than this gains nothing -
// matching that cadence keeps this endpoint's real upstream call (it isn't cached/persisted) from
// running any more often than the underlying data can actually change.
const POLL_INTERVAL_MS = 5 * 60_000

function formatKw(watts: number | null): string {
  if (watts === null) return '–'
  return `${(watts / 1000).toFixed(2)} kW`
}

// Grid/battery power is signed (see SolarLiveStatus) - positive means energy flowing in
// (importing / charging), negative means flowing out (exporting / discharging). Rendered as an
// unsigned magnitude plus a direction word rather than a bare signed number, which reads more
// naturally on a live dashboard tile than "-1.23 kW".
function formatDirectionalKw(watts: number | null, inLabel: string, outLabel: string): string {
  if (watts === null) return '–'
  if (watts === 0) return '0.00 kW'
  const magnitude = `${(Math.abs(watts) / 1000).toFixed(2)} kW`
  return watts > 0 ? `${magnitude} (${inLabel})` : `${magnitude} (${outLabel})`
}

// Relative ("Updated 0m 01s ago") rather than an absolute clock time, and synced to when THIS
// page last successfully polled (see lastPolledAt below) rather than the device's own reported
// reading time - a poll that lands right after a fresh Growatt report reads "0m 01s ago" here,
// confirming the page itself is alive and current, even though the underlying reading can still
// be up to ~5 minutes old (Growatt's own reporting cadence). Ticks up on its own between polls
// (see the `now` state below) so it visibly advances even between successful polls. Always
// minutes and seconds (not a tiered "just now"/"X min ago"/"Xh ago") so the figure reads
// consistently at any staleness.
function formatRelativeAge(sinceMs: number | null, now: number): string {
  if (sinceMs === null) return ''
  const diffSeconds = Math.max(0, Math.floor((now - sinceMs) / 1000))
  const minutes = Math.floor(diffSeconds / 60)
  const seconds = diffSeconds % 60
  return `${minutes}m ${seconds.toString().padStart(2, '0')}s ago`
}

interface Tile {
  label: string
  value: string
  accent: 'solar' | 'grid' | 'load' | 'battery'
}

function StatTile({ label, value, accent }: Tile) {
  return (
    <div className={`live-page__tile live-page__tile--${accent}`}>
      <div className="live-page__tile-label">{label}</div>
      <div className="live-page__tile-value">{value}</div>
    </div>
  )
}

// A hero figure rather than a StatTile - the day's cumulative total is the headline number on a
// live dashboard (everything else is a momentary reading that's already gone stale by the time
// you've read it), so it gets top billing, centred, in its own row.
function SolarTodayHero({ kwh }: { kwh: number | null }) {
  return (
    <div className="live-page__hero">
      <div className="live-page__hero-label">Total Solar Today</div>
      <div className="live-page__hero-value">{kwh !== null ? `${formatKwh(kwh)} kWh` : '–'}</div>
    </div>
  )
}

// Reuses the existing chart palette (solar green / standing-charge amber / battery red) rather
// than introducing new colours just for this - all three are already theme-aware (defined for
// both light and dark in index.css), so the bar stays consistent with the rest of the app's
// colour scheme in either theme without any extra work here.
function batterySocColor(percent: number): string {
  if (percent >= 50) return 'var(--chart-solar)'
  if (percent >= 25) return 'var(--chart-stdchg)'
  return 'var(--chart-battery)'
}

// A bar rather than a StatTile - state of charge is fundamentally a fraction of a whole (0-100%),
// which a bar communicates at a glance the way a bare number doesn't.
function BatterySocBar({ percent }: { percent: number | null }) {
  const clamped = percent !== null ? Math.max(0, Math.min(100, percent)) : 0
  return (
    <div className="live-page__battery-bar">
      <div className="live-page__battery-bar-header">
        <span>Battery SoC</span>
        <span>{percent !== null ? `${percent}%` : '–'}</span>
      </div>
      <div className="live-page__battery-bar-track">
        <div
          className="live-page__battery-bar-fill"
          style={{ width: `${clamped}%`, background: batterySocColor(clamped) }}
        />
      </div>
    </div>
  )
}

export default function Live() {
  const [available, setAvailable] = useState<boolean | null>(null)
  const [status, setStatus] = useState<SolarLiveStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())
  // When this page last successfully applied a poll's data - distinct from status.time (the
  // device's own reading time), see formatRelativeAge above for why.
  const [lastPolledAt, setLastPolledAt] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    solarIsAvailable()
      .then((result) => {
        if (!cancelled) setAvailable(result)
      })
      .catch(() => {
        if (!cancelled) setAvailable(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!available) return
    let cancelled = false

    function poll() {
      getSolarLive()
        .then((data) => {
          if (cancelled) return
          // A genuine Growatt API failure (rate limiting, a bad token, etc. - see
          // SolarLiveStatus.error) - surface it, but leave any already-displayed reading in
          // place rather than clearing it, so a transient outage doesn't also blank an
          // otherwise-still-useful last known value.
          if (data.error !== null) {
            setError(data.error)
            return
          }
          // A poll can also come back with every field null and no error - Growatt legitimately
          // has nothing to report yet for a single request (confirmed live; the backend already
          // retries once, see GrowattService.getLivePowerCurve, but a second consecutive miss
          // still reaches here). That's not a failure worth showing, so skip the update and keep
          // showing the last good reading (or the initial "Loading live data…" message, if this
          // is the very first poll) rather than blanking the whole dashboard for a minute - the
          // next successful poll replaces it as normal.
          if (data.time === null) return
          setStatus(data)
          setLastPolledAt(Date.now())
          setError(null)
        })
        .catch((err: unknown) => {
          if (cancelled) return
          setError(err instanceof Error ? err.message : 'Failed to load live data')
        })
    }

    poll()
    const interval = setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [available])

  // Separate from the data-poll interval above (which stays at POLL_INTERVAL_MS regardless) -
  // this just ticks the "Updated X ago" label so it counts up smoothly between polls, rather than
  // only updating whenever a poll happens to land.
  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [])

  return (
    <section className="live-page">
      <div className="live-page__heading-row">
        <h1>Live</h1>
        {lastPolledAt !== null && (
          <span className="live-page__as-of">Updated {formatRelativeAge(lastPolledAt, now)}</span>
        )}
      </div>

      {available === null && <p>Loading…</p>}
      {available === false && <p>Growatt isn&rsquo;t configured yet - see Admin → Manage Growatt Data.</p>}
      {available && error && <p className="live-page__error">{error}</p>}
      {available && !error && !status && <p>Loading live data…</p>}

      {available && status && (
        <>
          <SolarTodayHero kwh={status.solarTodayKwh} />

          <div className="live-page__grid">
            <StatTile label="Solar" value={formatKw(status.solarWatts)} accent="solar" />
            <StatTile
              label="Grid"
              value={formatDirectionalKw(status.gridWatts, 'Import', 'Export')}
              accent="grid"
            />
            <StatTile
              label="Battery"
              value={formatDirectionalKw(status.batteryWatts, 'Charging', 'Discharging')}
              accent="battery"
            />
            <StatTile label="Load" value={formatKw(status.loadWatts)} accent="load" />
          </div>

          <BatterySocBar percent={status.batterySoc} />

          <div className="live-page__chart">
            <h2 className="live-page__chart-title">Solar today (kW)</h2>
            <SolarTodayChart points={status.points} />
          </div>
        </>
      )}
    </section>
  )
}
