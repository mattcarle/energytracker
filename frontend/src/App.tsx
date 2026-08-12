import { useCallback, useEffect, useRef, useState } from 'react'
import { getCurrentUser, getSetupStatus, logout } from './api/client'
import type { AuthUser } from './api/types'
import ChangePassword from './pages/ChangePassword'
import Login from './pages/Login'
import ManageData from './pages/ManageData'
import ManageUsers from './pages/ManageUsers'
import Setup from './pages/Setup'
import UsageByDay from './pages/UsageByDay'
import UsageByHalfHour from './pages/UsageByHalfHour'
import UsageByMonth from './pages/UsageByMonth'
import UsageByWeek from './pages/UsageByWeek'
import UsageByYear from './pages/UsageByYear'
import './App.css'

type UsagePage = 'usage-half-hour' | 'usage-day' | 'usage-week' | 'usage-month' | 'usage-year'
type AdminPage = 'manage-data' | 'manage-users'
type Page = UsagePage | AdminPage
type AuthPhase = 'loading' | 'setup' | 'login' | 'change-password' | 'authenticated'

const USAGE_PAGES: { page: UsagePage; label: string }[] = [
  { page: 'usage-half-hour', label: 'Usage by half-hour' },
  { page: 'usage-day', label: 'Usage by day' },
  { page: 'usage-week', label: 'Usage by week' },
  { page: 'usage-month', label: 'Usage by month' },
  { page: 'usage-year', label: 'Usage by year' },
]

function App() {
  const [phase, setPhase] = useState<AuthPhase>('loading')
  const [user, setUser] = useState<AuthUser | null>(null)
  const [page, setPage] = useState<Page>('usage-day')
  const [usageMenuOpen, setUsageMenuOpen] = useState(false)
  const [adminMenuOpen, setAdminMenuOpen] = useState(false)
  const usageMenuRef = useRef<HTMLDivElement>(null)
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
    if (!usageMenuOpen && !adminMenuOpen) return

    function handlePointerDown(event: MouseEvent) {
      if (usageMenuOpen && usageMenuRef.current && !usageMenuRef.current.contains(event.target as Node)) {
        setUsageMenuOpen(false)
      }
      if (adminMenuOpen && adminMenuRef.current && !adminMenuRef.current.contains(event.target as Node)) {
        setAdminMenuOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setUsageMenuOpen(false)
        setAdminMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [usageMenuOpen, adminMenuOpen])

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
      setPage('usage-day')
      setPhase('login')
    })
  }

  function selectUsagePage(target: UsagePage) {
    setPage(target)
    setUsageMenuOpen(false)
  }

  function selectAdminPage(target: AdminPage) {
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

  const onUsagePage = USAGE_PAGES.some((p) => p.page === page)
  const onAdminPage = page === 'manage-data' || page === 'manage-users'

  return (
    <div className="app-shell">
      <nav className="app-nav">
        <span className="app-nav__title">Energy Tracker</span>
        <div className="app-nav__links">
          <div className="app-nav__dropdown" ref={usageMenuRef}>
            <button
              type="button"
              className={onUsagePage ? 'active' : ''}
              onClick={() => setUsageMenuOpen((open) => !open)}
              aria-expanded={usageMenuOpen}
            >
              Usage ▾
            </button>
            {usageMenuOpen && (
              <div className="app-nav__dropdown-panel">
                {USAGE_PAGES.map(({ page: usagePage, label }) => (
                  <button
                    key={usagePage}
                    type="button"
                    className={page === usagePage ? 'active' : ''}
                    onClick={() => selectUsagePage(usagePage)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
          </div>
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
      {page === 'usage-half-hour' && <UsageByHalfHour />}
      {page === 'usage-day' && <UsageByDay />}
      {page === 'usage-week' && <UsageByWeek />}
      {page === 'usage-month' && <UsageByMonth />}
      {page === 'usage-year' && <UsageByYear />}
      {page === 'manage-data' && user?.role === 'ADMIN' && <ManageData />}
      {page === 'manage-users' && user?.role === 'ADMIN' && <ManageUsers currentUserId={user.id} />}
    </div>
  )
}

export default App
