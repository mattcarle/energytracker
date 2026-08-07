import { useEffect, useState, type FormEvent } from 'react'
import { createUser, deleteUser, getUsers } from '../api/client'
import type { AuthUser, UserRole } from '../api/types'
import './UserManagement.css'

interface UserManagementProps {
  currentUserId: number
}

export default function UserManagement({ currentUserId }: UserManagementProps) {
  const [users, setUsers] = useState<AuthUser[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [username, setUsername] = useState('')
  const [initialPassword, setInitialPassword] = useState('')
  const [role, setRole] = useState<UserRole>('USER')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function refresh() {
    getUsers()
      .then(setUsers)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load users'))
  }

  useEffect(refresh, [])

  function handleCreate(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setSubmitting(true)
    createUser(username, initialPassword, role)
      .then(() => {
        setUsername('')
        setInitialPassword('')
        setRole('USER')
        refresh()
      })
      .catch((err: unknown) => setFormError(err instanceof Error ? err.message : 'Failed to create user'))
      .finally(() => setSubmitting(false))
  }

  function handleDelete(id: number) {
    setError(null)
    deleteUser(id)
      .then(refresh)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to delete user'))
  }

  return (
    <section className="user-management">
      <h1>Users</h1>

      {error && <p className="user-management__error">{error}</p>}
      {!error && !users && <p>Loading users…</p>}

      {users && (
        <table className="user-management__table">
          <thead>
            <tr>
              <th>Username</th>
              <th>Role</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.role}</td>
                <td>{user.mustChangePassword ? 'Password change pending' : 'Active'}</td>
                <td>
                  {user.id !== currentUserId && (
                    <button type="button" onClick={() => handleDelete(user.id)}>
                      Remove
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Add user</h2>
      <form className="user-management__form" onSubmit={handleCreate}>
        <label>
          <span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          <span>Initial password</span>
          <input
            type="password"
            value={initialPassword}
            onChange={(e) => setInitialPassword(e.target.value)}
            minLength={8}
            required
          />
        </label>
        <label>
          <span>Role</span>
          <select value={role} onChange={(e) => setRole(e.target.value as UserRole)}>
            <option value="USER">User</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Adding…' : 'Add user'}
        </button>
      </form>
      {formError && <p className="user-management__error">{formError}</p>}
    </section>
  )
}
