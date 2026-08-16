import { useState, type FormEvent } from 'react'
import { changePassword } from '../api/client'
import PasswordInput from '../components/PasswordInput'
import './ChangePasswordSettings.css'

interface ChangePasswordSettingsProps {
  username: string
}

export default function ChangePasswordSettings({ username }: ChangePasswordSettingsProps) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSuccess(false)

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setSubmitting(true)
    changePassword(currentPassword, newPassword)
      .then(() => {
        setCurrentPassword('')
        setNewPassword('')
        setConfirmPassword('')
        setSuccess(true)
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to change password'))
      .finally(() => setSubmitting(false))
  }

  return (
    <section className="change-password-settings">
      <h1>Change Password</h1>

      <form className="change-password-settings__form" onSubmit={handleSubmit}>
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
        <label>
          <span>Current password</span>
          <PasswordInput
            value={currentPassword}
            onChange={setCurrentPassword}
            autoComplete="current-password"
            required
            autoFocus
          />
        </label>
        <label>
          <span>New password</span>
          <PasswordInput
            value={newPassword}
            onChange={setNewPassword}
            minLength={8}
            autoComplete="new-password"
            required
          />
        </label>
        <label>
          <span>Confirm new password</span>
          <PasswordInput
            value={confirmPassword}
            onChange={setConfirmPassword}
            minLength={8}
            autoComplete="new-password"
            required
          />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Updating…' : 'Change password'}
        </button>
      </form>
      {error && <p className="change-password-settings__error">{error}</p>}
      {success && <p className="change-password-settings__success">Password updated.</p>}
    </section>
  )
}
