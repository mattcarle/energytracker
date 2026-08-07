import { useState, type FormEvent } from 'react'
import { setupAdmin } from '../api/client'
import type { AuthUser } from '../api/types'
import './AuthScreen.css'

interface SetupProps {
  onComplete: (user: AuthUser) => void
}

export default function Setup({ onComplete }: SetupProps) {
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setSubmitting(true)
    setupAdmin(password)
      .then(onComplete)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to create admin account'))
      .finally(() => setSubmitting(false))
  }

  return (
    <div className="auth-screen">
      <div className="auth-screen__card">
        <h1 className="auth-screen__title">Welcome to Energy Tracker</h1>
        <p className="auth-screen__subtitle">
          This is the first time the app has run. Choose a password for the admin account
          (username: <code>admin</code>).
        </p>
        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span>Admin password</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              required
              autoFocus
            />
          </label>
          <label className="auth-form__field">
            <span>Confirm password</span>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={8}
              required
            />
          </label>
          {error && <p className="auth-form__error">{error}</p>}
          <button type="submit" className="auth-form__submit" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create admin account'}
          </button>
        </form>
      </div>
    </div>
  )
}
