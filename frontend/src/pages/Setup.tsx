import { useState, type FormEvent } from 'react'
import { setupAdmin } from '../api/client'
import type { AuthUser, SetupResult } from '../api/types'
import DataIntegrityReportView from '../components/DataIntegrityReportView'
import Modal from '../components/Modal'
import PasswordInput from '../components/PasswordInput'
import './AuthScreen.css'

interface SetupProps {
  onComplete: (user: AuthUser) => void
}

export default function Setup({ onComplete }: SetupProps) {
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [octopusAccountNumber, setOctopusAccountNumber] = useState('')
  const [octopusAuthToken, setOctopusAuthToken] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SetupResult | null>(null)
  const [showIntegrityReport, setShowIntegrityReport] = useState(false)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setSubmitting(true)
    setupAdmin(password, octopusAccountNumber, octopusAuthToken)
      .then((setupResult) => {
        setResult(setupResult)
        setShowIntegrityReport(true)
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to create admin account'))
      .finally(() => setSubmitting(false))
  }

  if (result) {
    return (
      <div className="auth-screen">
        <div className="auth-screen__card">
          <h1 className="auth-screen__title">You&apos;re all set</h1>
          <p className="auth-screen__subtitle">Admin account created and Octopus Energy connected.</p>
          <p>
            {result.accountLoad.error
              ? `Loading account data failed: ${result.accountLoad.error}`
              : `Loaded ${result.accountLoad.meterPointCount} meter point(s) and ${result.accountLoad.meterCount} meter(s).`}
          </p>
          <p>
            {result.usageLoad.error
              ? `Loading usage data failed: ${result.usageLoad.error}`
              : `Loaded ${result.usageLoad.usageCount} usage record(s).`}
          </p>
          <button type="button" className="auth-form__submit" onClick={() => onComplete(result.user)}>
            Continue
          </button>
        </div>
        {showIntegrityReport && (
          <Modal
            title="Data integrity check"
            onDismiss={() => setShowIntegrityReport(false)}
            actions={
              <button
                type="button"
                className="modal__button--primary"
                onClick={() => setShowIntegrityReport(false)}
              >
                Close
              </button>
            }
          >
            <p>Here&rsquo;s how the data just loaded from Octopus Energy looks:</p>
            <DataIntegrityReportView report={result.integrityReport} />
          </Modal>
        )}
      </div>
    )
  }

  return (
    <div className="auth-screen">
      <div className="auth-screen__card">
        <h1 className="auth-screen__title">Welcome to Energy Tracker</h1>
        <p className="auth-screen__subtitle">
          This is the first time the app has run. Choose a password for the admin account
          (username: <code>admin</code>) and connect your Octopus Energy account.
        </p>
        <form className="auth-form" onSubmit={handleSubmit}>
          {/* Fixed username, with no visible input for it - this gives the browser's password
              manager something to associate the admin password with, so it doesn't have to ask
              the user to fill in a username by hand when offering to save it. */}
          <input
            type="text"
            name="username"
            value="admin"
            autoComplete="username"
            readOnly
            className="visually-hidden"
            aria-hidden="true"
            tabIndex={-1}
          />
          <label className="auth-form__field">
            <span>Admin password</span>
            <PasswordInput
              value={password}
              onChange={setPassword}
              minLength={8}
              autoComplete="new-password"
              required
              autoFocus
            />
          </label>
          <label className="auth-form__field">
            <span>Confirm password</span>
            <PasswordInput
              value={confirmPassword}
              onChange={setConfirmPassword}
              minLength={8}
              autoComplete="new-password"
              required
            />
          </label>
          <label className="auth-form__field">
            <span>Octopus account number</span>
            <input
              value={octopusAccountNumber}
              onChange={(e) => setOctopusAccountNumber(e.target.value)}
              placeholder="A-XXXXXXXX"
              required
            />
          </label>
          <label className="auth-form__field">
            <span>Octopus API auth token</span>
            <PasswordInput
              value={octopusAuthToken}
              onChange={setOctopusAuthToken}
              placeholder="sk_live_..."
              autoComplete="off"
              required
            />
          </label>
          {error && <p className="auth-form__error">{error}</p>}
          <button type="submit" className="auth-form__submit" disabled={submitting}>
            {submitting ? 'Setting up…' : 'Create admin account'}
          </button>
        </form>
      </div>
    </div>
  )
}
