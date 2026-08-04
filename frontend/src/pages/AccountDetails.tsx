import { useEffect, useState } from 'react'
import { getAgreements, getMeterPoints, getMeters } from '../api/client'
import type { Agreement, Meter, MeterPoint } from '../api/types'
import './AccountDetails.css'

interface MeterPointDetails {
  meterPoint: MeterPoint
  meters: Meter[]
  currentAgreement: Agreement | null
}

function pickCurrentAgreement(agreements: Agreement[]): Agreement | null {
  if (agreements.length === 0) return null
  const active = agreements.find((agreement) => agreement.validTo === null)
  if (active) return active
  return [...agreements].sort(
    (a, b) => new Date(b.validFrom).getTime() - new Date(a.validFrom).getTime(),
  )[0]
}

function formatDate(value: string | null): string {
  if (!value) return 'ongoing'
  return new Date(value).toLocaleDateString()
}

export default function AccountDetails() {
  const [details, setDetails] = useState<MeterPointDetails[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    Promise.all([getMeterPoints(), getMeters(), getAgreements()])
      .then(([meterPoints, meters, agreements]) => {
        if (cancelled) return
        const combined = meterPoints.map((meterPoint) => ({
          meterPoint,
          meters: meters.filter((meter) => meter.meterPointId === meterPoint.id),
          currentAgreement: pickCurrentAgreement(
            agreements.filter((agreement) => agreement.meterPointId === meterPoint.id),
          ),
        }))
        setDetails(combined)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load account details')
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="account-details">
      <h1>Account details</h1>

      {error && <p className="account-details__error">{error}</p>}
      {!error && !details && <p>Loading account details…</p>}
      {!error && details && details.length === 0 && <p>No meter points found.</p>}

      {details && details.length > 0 && (
        <div className="account-details__grid">
          {details.map(({ meterPoint, meters, currentAgreement }) => (
            <article className="meter-card" key={meterPoint.id}>
              <header className="meter-card__header">
                <h2>{meterPoint.meterType}</h2>
                <span className="meter-card__badge">
                  {meterPoint.isExport ? 'Export' : 'Import'}
                </span>
              </header>

              <dl className="meter-card__list">
                <dt>MPAN</dt>
                <dd>{meterPoint.mpan}</dd>

                <dt>Meter{meters.length === 1 ? '' : 's'}</dt>
                <dd>
                  {meters.length > 0
                    ? meters.map((meter) => meter.serialNumber).join(', ')
                    : 'None on record'}
                </dd>

                <dt>Tariff</dt>
                <dd>{currentAgreement?.tariffCode ?? 'No active agreement'}</dd>

                {currentAgreement && (
                  <>
                    <dt>Valid</dt>
                    <dd>
                      {formatDate(currentAgreement.validFrom)} &ndash;{' '}
                      {formatDate(currentAgreement.validTo)}
                    </dd>
                  </>
                )}
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
