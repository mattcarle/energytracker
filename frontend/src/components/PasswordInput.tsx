import { useState, type ChangeEvent } from 'react'
import './PasswordInput.css'

interface PasswordInputProps {
  value: string
  onChange: (value: string) => void
  required?: boolean
  minLength?: number
  autoFocus?: boolean
  placeholder?: string
  autoComplete?: string
}

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-7 0-11-7-11-7a21.8 21.8 0 0 1 5.06-5.94M9.9 4.24A10.94 10.94 0 0 1 12 4c7 0 11 7 11 7a21.8 21.8 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  )
}

// Toggling to type="text" only affects this input's own rendering, so the reveal is local and
// temporary - the underlying form state is always the plain string the caller already holds.
export default function PasswordInput({
  value,
  onChange,
  required,
  minLength,
  autoFocus,
  placeholder,
  autoComplete,
}: PasswordInputProps) {
  const [revealed, setRevealed] = useState(false)

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    onChange(event.target.value)
  }

  return (
    <div className="password-input">
      <input
        type={revealed ? 'text' : 'password'}
        value={value}
        onChange={handleChange}
        required={required}
        minLength={minLength}
        autoFocus={autoFocus}
        placeholder={placeholder}
        autoComplete={autoComplete}
      />
      <button
        type="button"
        className="password-input__toggle"
        onClick={() => setRevealed((r) => !r)}
        aria-label={revealed ? 'Hide password' : 'Show password'}
        aria-pressed={revealed}
      >
        {revealed ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </div>
  )
}
