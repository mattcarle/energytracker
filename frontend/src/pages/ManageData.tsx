import { useCallback, useEffect, useState } from 'react'
import {
  checkDataIntegrity,
  createDayAndNightTariff,
  createHappyHour,
  deleteDayAndNightTariff,
  deleteHappyHour,
  getAgreements,
  getDayAndNightTariffStatus,
  getHappyHours,
  getMeterPoints,
  getMeters,
  getUsageDateRanges,
  loadAccountData,
  loadUsageData,
  updateDayAndNightTariff,
} from '../api/client'
import type {
  AccountLoadResult,
  Agreement,
  DataIntegrityReport,
  DayAndNightTariffStatus,
  HappyHour,
  Meter,
  MeterPoint,
  UsageDateRange,
  UsageLoadResult,
} from '../api/types'
import DataIntegrityReportView from '../components/DataIntegrityReportView'
import Modal from '../components/Modal'
import './ManageData.css'

// LocalTime values ("HH:mm:ss") for every half-hourly interval in a day, matching the
// half-hourly unit rate granularity these valid-from times need to align with.
const HALF_HOUR_OPTIONS = Array.from({ length: 48 }, (_, i) => {
  const hours = String(Math.floor(i / 2)).padStart(2, '0')
  const minutes = i % 2 === 0 ? '00' : '30'
  return `${hours}:${minutes}:00`
})

function formatTime(value: string): string {
  return value.slice(0, 5)
}

interface DayAndNightEdit {
  id: number | null
  tariffCode: string
  dayRateValidFrom: string
  nightRateValidFrom: string
}

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

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

interface NewHappyHour {
  validFrom: string
  validTo: string
  rate: string
}

const EMPTY_HAPPY_HOUR: NewHappyHour = { validFrom: '', validTo: '', rate: '' }

interface ManageDataProps {
  // Notified with the latest tariff statuses every time this page fetches them, so App can keep
  // its "is Day/Night setup still incomplete" gate in sync without a second fetch of its own.
  onTariffStatusChange?: (statuses: DayAndNightTariffStatus[]) => void
}

export default function ManageData({ onTariffStatusChange }: ManageDataProps) {
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

  const [loadingIntegrity, setLoadingIntegrity] = useState(false)
  const [integrityReport, setIntegrityReport] = useState<DataIntegrityReport | null>(null)

  const [dayAndNightTariffs, setDayAndNightTariffs] = useState<DayAndNightTariffStatus[] | null>(null)
  const [editingTariff, setEditingTariff] = useState<DayAndNightEdit | null>(null)
  const [savingTariff, setSavingTariff] = useState(false)
  const [tariffActionError, setTariffActionError] = useState<string | null>(null)
  const [deleteTariffTarget, setDeleteTariffTarget] = useState<DayAndNightTariffStatus | null>(null)
  const [deletingTariff, setDeletingTariff] = useState(false)

  const [happyHours, setHappyHours] = useState<HappyHour[] | null>(null)
  const [newHappyHour, setNewHappyHour] = useState<NewHappyHour>(EMPTY_HAPPY_HOUR)
  const [addingHappyHour, setAddingHappyHour] = useState(false)
  const [deletingHappyHourId, setDeletingHappyHourId] = useState<number | null>(null)
  const [happyHourActionError, setHappyHourActionError] = useState<string | null>(null)

  const refresh = useCallback(() => {
    setError(null)
    Promise.all([
      getMeterPoints(),
      getMeters(),
      getAgreements(),
      getUsageDateRanges(),
      getDayAndNightTariffStatus(),
      getHappyHours(),
    ])
      .then(([meterPoints, meters, agreements, ranges, tariffStatuses, happyHourEntries]) => {
        const combined = meterPoints.map((meterPoint) => ({
          meterPoint,
          meters: meters.filter((meter) => meter.meterPointId === meterPoint.id),
          currentAgreement: pickCurrentAgreement(
            agreements.filter((agreement) => agreement.meterPointId === meterPoint.id),
          ),
        }))
        setDetails(combined)
        setDateRanges(new Map(ranges.map((r) => [r.mpan, r])))
        setDayAndNightTariffs(tariffStatuses)
        onTariffStatusChange?.(tariffStatuses)
        setHappyHours(happyHourEntries)
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load account details')
      })
  }, [onTariffStatusChange])

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

  function performIntegrityCheck() {
    setActionError(null)
    setLoadingIntegrity(true)
    checkDataIntegrity()
      .then(setIntegrityReport)
      .catch((err: unknown) => setActionError(err instanceof Error ? err.message : 'Failed to check data integrity'))
      .finally(() => setLoadingIntegrity(false))
  }

  function openTariffModal(tariff: DayAndNightTariffStatus) {
    setTariffActionError(null)
    setEditingTariff({
      id: tariff.id,
      tariffCode: tariff.tariffCode,
      dayRateValidFrom: tariff.dayRateValidFrom ?? HALF_HOUR_OPTIONS[0],
      nightRateValidFrom: tariff.nightRateValidFrom ?? HALF_HOUR_OPTIONS[0],
    })
  }

  function handleSaveTariff() {
    if (!editingTariff) return
    setTariffActionError(null)
    setSavingTariff(true)
    const save = editingTariff.id
      ? updateDayAndNightTariff(editingTariff.id, editingTariff.tariffCode, editingTariff.dayRateValidFrom, editingTariff.nightRateValidFrom)
      : createDayAndNightTariff(editingTariff.tariffCode, editingTariff.dayRateValidFrom, editingTariff.nightRateValidFrom)
    save
      .then(() => {
        setEditingTariff(null)
        refresh()
      })
      .catch((err: unknown) => setTariffActionError(err instanceof Error ? err.message : 'Failed to save tariff'))
      .finally(() => setSavingTariff(false))
  }

  function handleDeleteTariff() {
    if (deleteTariffTarget?.id == null) return
    setTariffActionError(null)
    setDeletingTariff(true)
    deleteDayAndNightTariff(deleteTariffTarget.id)
      .then(() => {
        setDeleteTariffTarget(null)
        refresh()
      })
      .catch((err: unknown) => setTariffActionError(err instanceof Error ? err.message : 'Failed to delete tariff'))
      .finally(() => setDeletingTariff(false))
  }

  const canAddHappyHour = newHappyHour.validFrom !== '' && newHappyHour.validTo !== '' && newHappyHour.rate !== ''

  function handleAddHappyHour() {
    if (!canAddHappyHour) return
    setHappyHourActionError(null)
    setAddingHappyHour(true)
    createHappyHour(newHappyHour.validFrom, newHappyHour.validTo, Number(newHappyHour.rate))
      .then(() => {
        setNewHappyHour(EMPTY_HAPPY_HOUR)
        refresh()
      })
      .catch((err: unknown) => setHappyHourActionError(err instanceof Error ? err.message : 'Failed to add happy hour'))
      .finally(() => setAddingHappyHour(false))
  }

  function handleDeleteHappyHour(id: number) {
    setHappyHourActionError(null)
    setDeletingHappyHourId(id)
    deleteHappyHour(id)
      .then(() => refresh())
      .catch((err: unknown) => setHappyHourActionError(err instanceof Error ? err.message : 'Failed to delete happy hour'))
      .finally(() => setDeletingHappyHourId(null))
  }

  return (
    <section className="manage-data">
      <h1>Manage Octopus Data</h1>

      {error && <p className="manage-data__error">{error}</p>}
      {!error && !details && <p>Loading account details…</p>}
      {!error && details && details.length === 0 && <p>No meter points found.</p>}

      {details && details.length > 0 && (
        <div className="manage-data__grid">
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

      <div className="manage-data__actions">
        <div className="manage-data__action-card">
          <h2>Refresh account data</h2>
          <p>Reloads meter points, meters, agreements, standing charges and unit rates from Octopus Energy.</p>
          <label className="manage-data__checkbox">
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

        <div className="manage-data__action-card">
          <h2>Refresh usage data</h2>
          <p>Reloads consumption data from Octopus Energy for all meter points.</p>
          <label className="manage-data__checkbox">
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

        <div className="manage-data__action-card">
          <h2>Data integrity check</h2>
          <p>
            Checks that agreements, standing charges, unit rates and usage readings are contiguous
            for every MPAN, with no gaps between records.
          </p>
          <button type="button" onClick={performIntegrityCheck} disabled={loadingIntegrity}>
            {loadingIntegrity ? 'Checking…' : 'Check Data Integrity'}
          </button>
        </div>
      </div>

      {actionError && <p className="manage-data__error">{actionError}</p>}

      <section className="manage-data__section">
        <h2>Day and Night Tariffs</h2>
        <p className="manage-data__section-intro">
          Some tariffs bill electricity at separate Day and Night rates, but Octopus doesn&rsquo;t report when
          each rate applies - set the valid-from times here so usage can be split correctly.
        </p>

        {!error && !dayAndNightTariffs && <p>Loading tariffs…</p>}
        {dayAndNightTariffs && dayAndNightTariffs.length === 0 && <p>No tariffs require Day/Night rates.</p>}

        {dayAndNightTariffs && dayAndNightTariffs.length > 0 && (
          <table className="day-night-table">
            <thead>
              <tr>
                <th>Tariff Code</th>
                <th>Day Rate Valid From</th>
                <th>Night Rate Valid From</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {dayAndNightTariffs.map((tariff) => (
                <tr key={tariff.tariffCode}>
                  <td>{tariff.tariffCode}</td>
                  <td>{tariff.dayRateValidFrom ? formatTime(tariff.dayRateValidFrom) : '—'}</td>
                  <td>{tariff.nightRateValidFrom ? formatTime(tariff.nightRateValidFrom) : '—'}</td>
                  <td className="day-night-table__actions">
                    {tariff.id === null ? (
                      <button type="button" onClick={() => openTariffModal(tariff)}>
                        Add
                      </button>
                    ) : (
                      <>
                        <button type="button" onClick={() => openTariffModal(tariff)}>
                          Update
                        </button>
                        <button
                          type="button"
                          className="day-night-table__delete"
                          onClick={() => setDeleteTariffTarget(tariff)}
                        >
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="manage-data__section">
        <h2>Happy Hours</h2>
        <p className="manage-data__section-intro">
          Record periods when electricity is free or discounted (e.g. an Octopus Saving Session or
          Power-Up) - usage during these windows is costed at the rate given here instead of the
          normal tariff rate.
        </p>

        {happyHourActionError && <p className="manage-data__error">{happyHourActionError}</p>}

        {!error && !happyHours && <p>Loading happy hours…</p>}
        {happyHours && happyHours.length === 0 && <p>No happy hours configured.</p>}

        {happyHours && happyHours.length > 0 && (
          <table className="happy-hour-table">
            <thead>
              <tr>
                <th>From</th>
                <th>To</th>
                <th>Rate (£/kWh)</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {happyHours.map((happyHour) => (
                <tr key={happyHour.id}>
                  <td>{formatDateTime(happyHour.validFrom)}</td>
                  <td>{formatDateTime(happyHour.validTo)}</td>
                  <td>£{happyHour.rate.toFixed(4)}</td>
                  <td className="day-night-table__actions">
                    <button
                      type="button"
                      className="day-night-table__delete"
                      onClick={() => handleDeleteHappyHour(happyHour.id)}
                      disabled={deletingHappyHourId === happyHour.id}
                    >
                      {deletingHappyHourId === happyHour.id ? 'Deleting…' : 'Delete'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="happy-hour-form">
          <label className="happy-hour-form__field">
            From
            <input
              type="datetime-local"
              value={newHappyHour.validFrom}
              onChange={(e) => setNewHappyHour({ ...newHappyHour, validFrom: e.target.value })}
            />
          </label>
          <label className="happy-hour-form__field">
            To
            <input
              type="datetime-local"
              value={newHappyHour.validTo}
              onChange={(e) => setNewHappyHour({ ...newHappyHour, validTo: e.target.value })}
            />
          </label>
          <label className="happy-hour-form__field">
            Rate (£/kWh)
            <input
              type="number"
              step="0.0001"
              min="0"
              placeholder="0.0000"
              value={newHappyHour.rate}
              onChange={(e) => setNewHappyHour({ ...newHappyHour, rate: e.target.value })}
            />
          </label>
          <button type="button" onClick={handleAddHappyHour} disabled={!canAddHappyHour || addingHappyHour}>
            {addingHappyHour ? 'Adding…' : 'Add'}
          </button>
        </div>
      </section>

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

      {editingTariff && (
        <Modal
          title={`${editingTariff.id === null ? 'Add' : 'Update'} ${editingTariff.tariffCode}`}
          onDismiss={() => setEditingTariff(null)}
          actions={
            <>
              <button
                type="button"
                className="modal__button--secondary"
                onClick={() => setEditingTariff(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="modal__button--primary"
                onClick={handleSaveTariff}
                disabled={savingTariff}
              >
                {savingTariff ? 'Saving…' : editingTariff.id === null ? 'Add' : 'Update'}
              </button>
            </>
          }
        >
          {tariffActionError && <p className="modal__error">{tariffActionError}</p>}
          <label className="day-night-modal__field">
            Day Rate Valid From
            <select
              value={editingTariff.dayRateValidFrom}
              onChange={(e) => setEditingTariff({ ...editingTariff, dayRateValidFrom: e.target.value })}
            >
              {HALF_HOUR_OPTIONS.map((time) => (
                <option key={time} value={time}>
                  {formatTime(time)}
                </option>
              ))}
            </select>
          </label>
          <label className="day-night-modal__field">
            Night Rate Valid From
            <select
              value={editingTariff.nightRateValidFrom}
              onChange={(e) => setEditingTariff({ ...editingTariff, nightRateValidFrom: e.target.value })}
            >
              {HALF_HOUR_OPTIONS.map((time) => (
                <option key={time} value={time}>
                  {formatTime(time)}
                </option>
              ))}
            </select>
          </label>
        </Modal>
      )}

      {deleteTariffTarget && (
        <Modal
          title="Delete Day and Night tariff?"
          onDismiss={() => setDeleteTariffTarget(null)}
          actions={
            <>
              <button
                type="button"
                className="modal__button--secondary"
                onClick={() => setDeleteTariffTarget(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="modal__button--danger"
                onClick={handleDeleteTariff}
                disabled={deletingTariff}
              >
                {deletingTariff ? 'Deleting…' : 'Delete'}
              </button>
            </>
          }
        >
          {tariffActionError && <p className="modal__error">{tariffActionError}</p>}
          <p>
            This will remove the configured Day/Night valid-from times for{' '}
            <strong>{deleteTariffTarget.tariffCode}</strong>.
          </p>
        </Modal>
      )}

      {integrityReport && (
        <Modal
          title="Data integrity check"
          onDismiss={() => setIntegrityReport(null)}
          actions={
            <button type="button" className="modal__button--primary" onClick={() => setIntegrityReport(null)}>
              Close
            </button>
          }
        >
          <DataIntegrityReportView report={integrityReport} />
        </Modal>
      )}
    </section>
  )
}
