import { useState, type FormEvent } from 'react'
import { login } from '../api/client'
import type { AuthUser } from '../api/types'
import PasswordInput from '../components/PasswordInput'
import './AuthScreen.css'

interface LoginProps {
  onComplete: (user: AuthUser) => void
}

export default function Login({ onComplete }: LoginProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    login(username, password)
      .then(onComplete)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to log in'))
      .finally(() => setSubmitting(false))
  }

  return (
    <div className="auth-screen">
      <div className="auth-screen__card">
        <h1 className="auth-screen__title">Energy Tracker</h1>
        <p className="auth-screen__subtitle">Sign in to continue.</p>
        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span>Username</span>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
              autoFocus
            />
          </label>
          <label className="auth-form__field">
            <span>Password</span>
            <PasswordInput value={password} onChange={setPassword} autoComplete="current-password" required />
          </label>
          {error && <p className="auth-form__error">{error}</p>}
          <button type="submit" className="auth-form__submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}
