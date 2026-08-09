import { useCallback, useEffect, useState } from 'react'
import { getCurrentUser, getSetupStatus, logout } from './api/client'
import type { AuthUser } from './api/types'
import Admin from './pages/Admin'
import ChangePassword from './pages/ChangePassword'
import Login from './pages/Login'
import Setup from './pages/Setup'
import UsageByDay from './pages/UsageByDay'
import UserManagement from './pages/UserManagement'
import './App.css'

type Page = 'admin' | 'usage' | 'users'
type AuthPhase = 'loading' | 'setup' | 'login' | 'change-password' | 'authenticated'

function App() {
  const [phase, setPhase] = useState<AuthPhase>('loading')
  const [user, setUser] = useState<AuthUser | null>(null)
  const [page, setPage] = useState<Page>('usage')

  const refreshAuth = useCallback(() => {
    setPhase('loading')
    getSetupStatus()
      .then(({ setupRequired }) => {
        if (setupRequired) {
          setPhase('setup')
          return
        }
        return getCurrentUser()
          .then((me) => {
            setUser(me)
            setPhase(me.mustChangePassword ? 'change-password' : 'authenticated')
          })
          .catch(() => setPhase('login'))
      })
      .catch(() => setPhase('login'))
  }, [])

  useEffect(() => {
    refreshAuth()
  }, [refreshAuth])

  function handleAuthenticated(authenticatedUser: AuthUser) {
    setUser(authenticatedUser)
    setPhase(authenticatedUser.mustChangePassword ? 'change-password' : 'authenticated')
  }

  function handlePasswordChanged() {
    setUser((current) => (current ? { ...current, mustChangePassword: false } : current))
    setPhase('authenticated')
  }

  function handleLogout() {
    logout().finally(() => {
      setUser(null)
      setPage('usage')
      setPhase('login')
    })
  }

  if (phase === 'loading') {
    return (
      <div className="app-shell">
        <p>Loading…</p>
      </div>
    )
  }

  if (phase === 'setup') {
    return <Setup onComplete={handleAuthenticated} />
  }

  if (phase === 'login') {
    return <Login onComplete={handleAuthenticated} />
  }

  if (phase === 'change-password' && user) {
    return <ChangePassword username={user.username} onComplete={handlePasswordChanged} />
  }

  return (
    <div className="app-shell">
      <nav className="app-nav">
        <span className="app-nav__title">Energy Tracker</span>
        <div className="app-nav__links">
          <button
            type="button"
            className={page === 'usage' ? 'active' : ''}
            onClick={() => setPage('usage')}
          >
            Usage
          </button>
          {user?.role === 'ADMIN' && (
            <button
              type="button"
              className={page === 'admin' ? 'active' : ''}
              onClick={() => setPage('admin')}
            >
              Admin
            </button>
          )}
          {user?.role === 'ADMIN' && (
            <button
              type="button"
              className={page === 'users' ? 'active' : ''}
              onClick={() => setPage('users')}
            >
              Users
            </button>
          )}
          <button type="button" onClick={handleLogout}>
            Log out ({user?.username})
          </button>
        </div>
      </nav>
      {page === 'admin' && user?.role === 'ADMIN' && <Admin />}
      {page === 'usage' && <UsageByDay />}
      {page === 'users' && user?.role === 'ADMIN' && <UserManagement currentUserId={user.id} />}
    </div>
  )
}

export default App
