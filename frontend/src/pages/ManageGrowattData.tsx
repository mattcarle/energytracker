import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { getGrowattCredentialsStatus, loadGrowattSolarData, saveGrowattCredentials } from '../api/client'
import type { GrowattCredentialsStatus, PlantLoadResult, SolarLoadResult } from '../api/types'
import Modal from '../components/Modal'
import PasswordInput from '../components/PasswordInput'
// Reuses ManageData's layout classes (.manage-data, .manage-data__action-card, etc.) rather
// than duplicating an near-identical settings-page stylesheet - this page follows the exact
// same card/checkbox/button layout, just with different content.
import './ManageData.css'

export default function ManageGrowattData() {
  const [status, setStatus] = useState<GrowattCredentialsStatus | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const [apiToken, setApiToken] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveResult, setSaveResult] = useState<PlantLoadResult | null>(null)

  const [deleteAllSolar, setDeleteAllSolar] = useState(false)
  const [confirmingLoad, setConfirmingLoad] = useState(false)
  const [loadingSolar, setLoadingSolar] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loadResult, setLoadResult] = useState<SolarLoadResult | null>(null)

  const refreshStatus = useCallback(() => {
    setStatusError(null)
    getGrowattCredentialsStatus()
      .then(setStatus)
      .catch((err: unknown) => setStatusError(err instanceof Error ? err.message : 'Failed to load Growatt status'))
  }, [])

  useEffect(() => {
    refreshStatus()
  }, [refreshStatus])

  function handleSaveToken(event: FormEvent) {
    event.preventDefault()
    setSaveError(null)
    setSaving(true)
    saveGrowattCredentials(apiToken)
      .then((result) => {
        setSaveResult(result)
        setApiToken('')
        refreshStatus()
      })
      .catch((err: unknown) => setSaveError(err instanceof Error ? err.message : 'Failed to save Growatt token'))
      .finally(() => setSaving(false))
  }

  function performLoadSolarData(deleteAll: boolean) {
    setLoadError(null)
    setLoadingSolar(true)
    loadGrowattSolarData(deleteAll)
      .then(setLoadResult)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'Failed to load solar data'))
      .finally(() => setLoadingSolar(false))
  }

  function handleLoadClick() {
    if (deleteAllSolar) setConfirmingLoad(true)
    else performLoadSolarData(false)
  }

  function handleConfirmDelete() {
    setConfirmingLoad(false)
    performLoadSolarData(true)
  }

  return (
    <section className="manage-data">
      <h1>Manage Growatt Data</h1>

      {statusError && <p className="manage-data__error">{statusError}</p>}

      <div className="manage-data__actions">
        <div className="manage-data__action-card">
          <h2>Growatt API Token</h2>
          <p>
            {status?.configured
              ? `Connected - plant ID ${status.plantId}.`
              : 'Not yet configured. Enter your Growatt API token to connect solar generation tracking.'}
          </p>
          <form onSubmit={handleSaveToken}>
            <label className="day-night-modal__field">
              API Token
              <PasswordInput value={apiToken} onChange={setApiToken} placeholder="Growatt API token" autoComplete="off" required />
            </label>
            {saveError && <p className="manage-data__error">{saveError}</p>}
            <button type="submit" disabled={saving || !apiToken}>
              {saving ? 'Saving…' : status?.configured ? 'Update Token' : 'Save Token'}
            </button>
          </form>
        </div>

        <div className="manage-data__action-card">
          <h2>Load Solar Data</h2>
          <p>Reloads solar generation data from Growatt for the connected plant.</p>
          <label className="manage-data__checkbox">
            <input
              type="checkbox"
              checked={deleteAllSolar}
              onChange={(e) => setDeleteAllSolar(e.target.checked)}
            />
            Delete existing solar data first
          </label>
          {loadError && <p className="manage-data__error">{loadError}</p>}
          <button type="button" onClick={handleLoadClick} disabled={loadingSolar || !status?.configured}>
            {loadingSolar ? 'Loading…' : 'Load Solar Data Now'}
          </button>
        </div>
      </div>

      {confirmingLoad && (
        <Modal
          title="Are you sure?"
          onDismiss={() => setConfirmingLoad(false)}
          actions={
            <>
              <button type="button" className="modal__button--secondary" onClick={() => setConfirmingLoad(false)}>
                Cancel
              </button>
              <button type="button" className="modal__button--danger" onClick={handleConfirmDelete}>
                Delete and reload
              </button>
            </>
          }
        >
          <p>This will permanently delete all existing solar generation data before reloading it from Growatt. This cannot be undone.</p>
        </Modal>
      )}

      {saveResult && (
        <Modal
          title={saveResult.error ? 'Failed to connect' : 'Growatt connected'}
          onDismiss={() => setSaveResult(null)}
          actions={
            <button type="button" className="modal__button--primary" onClick={() => setSaveResult(null)}>
              Close
            </button>
          }
        >
          {saveResult.error ? (
            <p className="modal__error">Error: {saveResult.error}</p>
          ) : (
            <dl>
              <dt>Plant</dt>
              <dd>{saveResult.plantName}</dd>
              <dt>Plant ID</dt>
              <dd>{saveResult.plantId}</dd>
              <dt>Install date</dt>
              <dd>{saveResult.installDate ?? 'Unknown'}</dd>
            </dl>
          )}
        </Modal>
      )}

      {loadResult && (
        <Modal
          title="Solar data refreshed"
          onDismiss={() => setLoadResult(null)}
          actions={
            <button type="button" className="modal__button--primary" onClick={() => setLoadResult(null)}>
              Close
            </button>
          }
        >
          {loadResult.error && <p className="modal__error">Error: {loadResult.error}</p>}
          <dl>
            <dt>Days loaded</dt>
            <dd>{loadResult.dayCount}</dd>
          </dl>
        </Modal>
      )}
    </section>
  )
}
