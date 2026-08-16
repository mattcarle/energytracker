import { useState, type FormEvent } from 'react'
import { changePassword } from '../api/client'
import PasswordInput from '../components/PasswordInput'
import './AuthScreen.css'

interface ChangePasswordProps {
  username: string
  onComplete: () => void
}

export default function ChangePassword({ username, onComplete }: ChangePasswordProps) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setSubmitting(true)
    changePassword(currentPassword, newPassword)
      .then(onComplete)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to change password'))
      .finally(() => setSubmitting(false))
  }

  return (
    <div className="auth-screen">
      <div className="auth-screen__card">
        <h1 className="auth-screen__title">Change your password</h1>
        <p className="auth-screen__subtitle">
          Hi {username}, this account was just created for you. Choose a new password before continuing.
        </p>
        <form className="auth-form" onSubmit={handleSubmit}>
          {/* No visible username field on this form - gives the browser's password manager
              something to associate the credential with, so it doesn't have to ask. */}
          <input
            type="text"
            name="username"
            value={username}
            autoComplete="username"
            readOnly
            className="visually-hidden"
            aria-hidden="true"
            tabIndex={-1}
          />
          <label className="auth-form__field">
            <span>Current (temporary) password</span>
            <PasswordInput
              value={currentPassword}
              onChange={setCurrentPassword}
              autoComplete="current-password"
              required
              autoFocus
            />
          </label>
          <label className="auth-form__field">
            <span>New password</span>
            <PasswordInput
              value={newPassword}
              onChange={setNewPassword}
              minLength={8}
              autoComplete="new-password"
              required
            />
          </label>
          <label className="auth-form__field">
            <span>Confirm new password</span>
            <PasswordInput
              value={confirmPassword}
              onChange={setConfirmPassword}
              minLength={8}
              autoComplete="new-password"
              required
            />
          </label>
          {error && <p className="auth-form__error">{error}</p>}
          <button type="submit" className="auth-form__submit" disabled={submitting}>
            {submitting ? 'Updating…' : 'Change password'}
          </button>
        </form>
      </div>
    </div>
  )
}
