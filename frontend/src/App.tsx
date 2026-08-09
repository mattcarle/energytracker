import { useCallback, useEffect, useRef, useState } from 'react'
import { getCurrentUser, getSetupStatus, logout } from './api/client'
import type { AuthUser } from './api/types'
import ChangePassword from './pages/ChangePassword'
import Login from './pages/Login'
import ManageData from './pages/ManageData'
import ManageUsers from './pages/ManageUsers'
import Setup from './pages/Setup'
import UsageByDay from './pages/UsageByDay'
import './App.css'

type Page = 'usage' | 'manage-data' | 'manage-users'
type AuthPhase = 'loading' | 'setup' | 'login' | 'change-password' | 'authenticated'

function App() {
  const [phase, setPhase] = useState<AuthPhase>('loading')
  const [user, setUser] = useState<AuthUser | null>(null)
  const [page, setPage] = useState<Page>('usage')
  const [adminMenuOpen, setAdminMenuOpen] = useState(false)
  const adminMenuRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (!adminMenuOpen) return

    function handlePointerDown(event: MouseEvent) {
      if (adminMenuRef.current && !adminMenuRef.current.contains(event.target as Node)) {
        setAdminMenuOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setAdminMenuOpen(false)
    }

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [adminMenuOpen])

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

  function selectAdminPage(target: 'manage-data' | 'manage-users') {
    setPage(target)
    setAdminMenuOpen(false)
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

  const onAdminPage = page === 'manage-data' || page === 'manage-users'

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
            <div className="app-nav__dropdown" ref={adminMenuRef}>
              <button
                type="button"
                className={onAdminPage ? 'active' : ''}
                onClick={() => setAdminMenuOpen((open) => !open)}
                aria-expanded={adminMenuOpen}
              >
                Admin ▾
              </button>
              {adminMenuOpen && (
                <div className="app-nav__dropdown-panel">
                  <button
                    type="button"
                    className={page === 'manage-data' ? 'active' : ''}
                    onClick={() => selectAdminPage('manage-data')}
                  >
                    Manage Data
                  </button>
                  <button
                    type="button"
                    className={page === 'manage-users' ? 'active' : ''}
                    onClick={() => selectAdminPage('manage-users')}
                  >
                    Manage Users
                  </button>
                </div>
              )}
            </div>
          )}
          <button type="button" onClick={handleLogout}>
            Log out ({user?.username})
          </button>
        </div>
      </nav>
      {page === 'manage-data' && user?.role === 'ADMIN' && <ManageData />}
      {page === 'usage' && <UsageByDay />}
      {page === 'manage-users' && user?.role === 'ADMIN' && <ManageUsers currentUserId={user.id} />}
    </div>
  )
}

export default App
