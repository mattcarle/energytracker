import { useCallback, useEffect, useState } from 'react'
import {
  getAgreements,
  getMeterPoints,
  getMeters,
  getUsageDateRanges,
  loadAccountData,
  loadUsageData,
} from '../api/client'
import type { AccountLoadResult, Agreement, Meter, MeterPoint, UsageDateRange, UsageLoadResult } from '../api/types'
import Modal from '../components/Modal'
import './Admin.css'

interface MeterPointDetails {
  meterPoint: MeterPoint
  meters: Meter[]
  currentAgreement: Agreement | null
}

type ConfirmTarget = 'account' | 'usage' | null
type SummaryState =
  | { type: 'account'; result: AccountLoadResult }
  | { type: 'usage'; result: UsageLoadResult }
  | null

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

export default function Admin() {
  const [details, setDetails] = useState<MeterPointDetails[] | null>(null)
  const [dateRanges, setDateRanges] = useState<Map<string, UsageDateRange> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [deleteAllAccount, setDeleteAllAccount] = useState(false)
  const [deleteAllUsage, setDeleteAllUsage] = useState(false)
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget>(null)
  const [summary, setSummary] = useState<SummaryState>(null)
  const [loadingAccount, setLoadingAccount] = useState(false)
  const [loadingUsage, setLoadingUsage] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const refresh = useCallback(() => {
    setError(null)
    Promise.all([getMeterPoints(), getMeters(), getAgreements(), getUsageDateRanges()])
      .then(([meterPoints, meters, agreements, ranges]) => {
        const combined = meterPoints.map((meterPoint) => ({
          meterPoint,
          meters: meters.filter((meter) => meter.meterPointId === meterPoint.id),
          currentAgreement: pickCurrentAgreement(
            agreements.filter((agreement) => agreement.meterPointId === meterPoint.id),
          ),
        }))
        setDetails(combined)
        setDateRanges(new Map(ranges.map((r) => [r.mpan, r])))
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load account details')
      })
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  function performRefreshAccount(deleteAll: boolean) {
    setActionError(null)
    setLoadingAccount(true)
    loadAccountData(deleteAll)
      .then((result) => {
        setSummary({ type: 'account', result })
        refresh()
      })
      .catch((err: unknown) => setActionError(err instanceof Error ? err.message : 'Failed to refresh account data'))
      .finally(() => setLoadingAccount(false))
  }

  function performRefreshUsage(deleteAll: boolean) {
    setActionError(null)
    setLoadingUsage(true)
    loadUsageData(deleteAll)
      .then((result) => {
        setSummary({ type: 'usage', result })
        refresh()
      })
      .catch((err: unknown) => setActionError(err instanceof Error ? err.message : 'Failed to refresh usage data'))
      .finally(() => setLoadingUsage(false))
  }

  function handleRefreshAccountClick() {
    if (deleteAllAccount) setConfirmTarget('account')
    else performRefreshAccount(false)
  }

  function handleRefreshUsageClick() {
    if (deleteAllUsage) setConfirmTarget('usage')
    else performRefreshUsage(false)
  }

  function handleConfirmDelete() {
    const target = confirmTarget
    setConfirmTarget(null)
    if (target === 'account') performRefreshAccount(true)
    if (target === 'usage') performRefreshUsage(true)
  }

  return (
    <section className="admin">
      <h1>Admin</h1>

      {error && <p className="admin__error">{error}</p>}
      {!error && !details && <p>Loading account details…</p>}
      {!error && details && details.length === 0 && <p>No meter points found.</p>}

      {details && details.length > 0 && (
        <div className="admin__grid">
          {details.map(({ meterPoint, meters, currentAgreement }) => {
            const usageRange = dateRanges?.get(meterPoint.mpan) ?? null
            return (
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

                  <dt>Usage loaded</dt>
                  <dd>
                    {usageRange
                      ? `${formatDate(usageRange.earliest)} – ${formatDate(usageRange.latest)}`
                      : 'No usage data loaded'}
                  </dd>
                </dl>
              </article>
            )
          })}
        </div>
      )}

      <div className="admin__actions">
        <div className="admin__action-card">
          <h2>Refresh account data</h2>
          <p>Reloads meter points, meters, agreements, standing charges and unit rates from Octopus Energy.</p>
          <label className="admin__checkbox">
            <input
              type="checkbox"
              checked={deleteAllAccount}
              onChange={(e) => setDeleteAllAccount(e.target.checked)}
            />
            Delete existing account data first
          </label>
          <button type="button" onClick={handleRefreshAccountClick} disabled={loadingAccount}>
            {loadingAccount ? 'Refreshing…' : 'Refresh Account Data'}
          </button>
        </div>

        <div className="admin__action-card">
          <h2>Refresh usage data</h2>
          <p>Reloads consumption data from Octopus Energy for all meter points.</p>
          <label className="admin__checkbox">
            <input
              type="checkbox"
              checked={deleteAllUsage}
              onChange={(e) => setDeleteAllUsage(e.target.checked)}
            />
            Delete existing usage data first
          </label>
          <button type="button" onClick={handleRefreshUsageClick} disabled={loadingUsage}>
            {loadingUsage ? 'Refreshing…' : 'Refresh Usage Data'}
          </button>
        </div>
      </div>

      {actionError && <p className="admin__error">{actionError}</p>}

      {confirmTarget && (
        <Modal
          title="Are you sure?"
          onDismiss={() => setConfirmTarget(null)}
          actions={
            <>
              <button
                type="button"
                className="modal__button--secondary"
                onClick={() => setConfirmTarget(null)}
              >
                Cancel
              </button>
              <button type="button" className="modal__button--danger" onClick={handleConfirmDelete}>
                Delete and reload
              </button>
            </>
          }
        >
          <p>
            This will permanently delete all existing {confirmTarget === 'account' ? 'account' : 'usage'}{' '}
            data before reloading it from Octopus Energy. This cannot be undone.
          </p>
        </Modal>
      )}

      {summary && (
        <Modal
          title={summary.type === 'account' ? 'Account data refreshed' : 'Usage data refreshed'}
          onDismiss={() => setSummary(null)}
          actions={
            <button type="button" className="modal__button--primary" onClick={() => setSummary(null)}>
              Close
            </button>
          }
        >
          {summary.result.error && <p className="modal__error">Error: {summary.result.error}</p>}
          {summary.type === 'account' ? (
            <dl>
              <dt>Meter points</dt>
              <dd>{summary.result.meterPointCount}</dd>
              <dt>Meters</dt>
              <dd>{summary.result.meterCount}</dd>
              <dt>Agreements</dt>
              <dd>{summary.result.agreementCount}</dd>
              <dt>Standing charges</dt>
              <dd>{summary.result.standingChargeCount}</dd>
              <dt>Unit rates</dt>
              <dd>{summary.result.unitRateCount}</dd>
              <dt>Half-hourly unit rates</dt>
              <dd>{summary.result.unitRatesByHalfHourCount}</dd>
              <dt>Daily standing charges</dt>
              <dd>{summary.result.standingChargesByDayCount}</dd>
            </dl>
          ) : (
            <dl>
              <dt>Usage records</dt>
              <dd>{summary.result.usageCount}</dd>
              <dt>UTC-to-local mappings</dt>
              <dd>{summary.result.utcToLocalCount}</dd>
            </dl>
          )}
        </Modal>
      )}
    </section>
  )
}
