import { useCallback, useEffect, useRef, useState } from 'react'
import { getCurrentUser, getDayAndNightTariffStatus, getSetupStatus, logout } from './api/client'
import type { AuthUser, DayAndNightTariffStatus } from './api/types'
import Modal from './components/Modal'
import { useIsMobile } from './hooks/useIsMobile'
import ChangePassword from './pages/ChangePassword'
import ChangePasswordSettings from './pages/ChangePasswordSettings'
import Login from './pages/Login'
import ManageData from './pages/ManageData'
import ManageUsers from './pages/ManageUsers'
import Setup from './pages/Setup'
import UsageByDay from './pages/UsageByDay'
import UsageByMonth from './pages/UsageByMonth'
import UsageByWeek from './pages/UsageByWeek'
import UsageByYear from './pages/UsageByYear'
import './App.css'

type UsagePage = 'usage-day' | 'usage-week' | 'usage-month' | 'usage-year'
type AdminPage = 'manage-data' | 'manage-users' | 'change-password-settings'
type Page = UsagePage | AdminPage
type AuthPhase = 'loading' | 'setup' | 'login' | 'change-password' | 'authenticated'

const USAGE_PAGES: { page: UsagePage; label: string }[] = [
  { page: 'usage-day', label: 'Day' },
  { page: 'usage-week', label: 'Week' },
  { page: 'usage-month', label: 'Month' },
  { page: 'usage-year', label: 'Year' },
]

function App() {
  const [phase, setPhase] = useState<AuthPhase>('loading')
  const [user, setUser] = useState<AuthUser | null>(null)
  const [page, setPage] = useState<Page>('usage-day')
  const [usageMenuOpen, setUsageMenuOpen] = useState(false)
  const [adminMenuOpen, setAdminMenuOpen] = useState(false)
  // Whether any tariff bills electricity at separate Day/Night rates but hasn't had its
  // valid-from times configured yet (see DayAndNightTariffController). While true, an admin is
  // confined to Manage Data - usage/insights are wrong everywhere else until it's resolved.
  const [needsDayAndNightSetup, setNeedsDayAndNightSetup] = useState(false)
  const [showDayAndNightDialog, setShowDayAndNightDialog] = useState(false)
  const usageMenuRef = useRef<HTMLDivElement>(null)
  const adminMenuRef = useRef<HTMLDivElement>(null)
  const isMobile = useIsMobile()

  // Only pops the dialog open on the false->true transition, so ManageData's own re-checks
  // (after every add/update/delete/refresh) don't re-show it while already known to be needed.
  const applyDayAndNightStatuses = useCallback((statuses: DayAndNightTariffStatus[]) => {
    const needsSetup = statuses.some((status) => status.id === null)
    setNeedsDayAndNightSetup((prev) => {
      if (needsSetup && !prev) setShowDayAndNightDialog(true)
      return needsSetup
    })
  }, [])

  const checkDayAndNightSetup = useCallback(
    (currentUser: AuthUser | null) => {
      // Only an admin can reach Manage Data to fix this, so only an admin gets confined by it -
      // otherwise a regular user would be locked on a page they can't even see.
      if (!currentUser || currentUser.role !== 'ADMIN') {
        setNeedsDayAndNightSetup(false)
        return
      }
      getDayAndNightTariffStatus().then(applyDayAndNightStatuses).catch(() => {})
    },
    [applyDayAndNightStatuses],
  )

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
            if (!me.mustChangePassword) checkDayAndNightSetup(me)
          })
          .catch(() => setPhase('login'))
      })
      .catch(() => setPhase('login'))
  }, [checkDayAndNightSetup])

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

  // The mobile nav has no Admin entry point at all, so a user (or window resized down) sitting
  // on an admin page would otherwise be stranded with no way back - bounce to Usage by day.
  // Skipped while Day/Night setup is incomplete: that redirect (below) is state-driven rather
  // than a nav click, and needs to stick on mobile too.
  useEffect(() => {
    if (needsDayAndNightSetup) return
    if (isMobile && (page === 'manage-data' || page === 'manage-users' || page === 'change-password-settings')) {
      setPage('usage-day')
    }
  }, [isMobile, page, needsDayAndNightSetup])

  // The actual enforcement confining an admin to Manage Data while setup is incomplete. Nav
  // controls are also hidden/disabled below, but this is what makes it stick regardless.
  useEffect(() => {
    if (needsDayAndNightSetup && page !== 'manage-data') {
      setPage('manage-data')
    }
  }, [needsDayAndNightSetup, page])

  function handleAuthenticated(authenticatedUser: AuthUser) {
    setUser(authenticatedUser)
    const needsPasswordChange = authenticatedUser.mustChangePassword
    setPhase(needsPasswordChange ? 'change-password' : 'authenticated')
    if (!needsPasswordChange) checkDayAndNightSetup(authenticatedUser)
  }

  function handlePasswordChanged() {
    const updated = user ? { ...user, mustChangePassword: false } : null
    setUser(updated)
    setPhase('authenticated')
    checkDayAndNightSetup(updated)
  }

  function handleLogout() {
    logout().finally(() => {
      setUser(null)
      setPage('usage-day')
      setPhase('login')
      setNeedsDayAndNightSetup(false)
      setShowDayAndNightDialog(false)
    })
  }

  function selectUsagePage(target: UsagePage) {
    if (needsDayAndNightSetup) return
    setPage(target)
    setUsageMenuOpen(false)
  }

  function selectAdminPage(target: AdminPage) {
    if (needsDayAndNightSetup && target !== 'manage-data') return
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
  const onAdminPage = page === 'manage-data' || page === 'manage-users' || page === 'change-password-settings'

  return (
    <div className="app-shell">
      <nav className="app-nav">
        <span className="app-nav__title">Energy Tracker</span>
        <div className="app-nav__links">
          {!isMobile && !needsDayAndNightSetup && (
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
          )}
          {!isMobile && user?.role === 'ADMIN' && (
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
                    disabled={needsDayAndNightSetup}
                    title={needsDayAndNightSetup ? 'Finish Day/Night tariff setup first' : undefined}
                  >
                    Manage Users
                  </button>
                  <button
                    type="button"
                    className={page === 'change-password-settings' ? 'active' : ''}
                    onClick={() => selectAdminPage('change-password-settings')}
                    disabled={needsDayAndNightSetup}
                    title={needsDayAndNightSetup ? 'Finish Day/Night tariff setup first' : undefined}
                  >
                    Change Password
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
      {isMobile && !needsDayAndNightSetup && (
        <div className="app-nav__mobile-tabs">
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
      {page === 'usage-day' && <UsageByDay />}
      {page === 'usage-week' && <UsageByWeek />}
      {page === 'usage-month' && <UsageByMonth />}
      {page === 'usage-year' && <UsageByYear />}
      {page === 'manage-data' && user?.role === 'ADMIN' && (
        <ManageData onTariffStatusChange={applyDayAndNightStatuses} />
      )}
      {page === 'manage-users' && user?.role === 'ADMIN' && <ManageUsers currentUserId={user.id} />}
      {page === 'change-password-settings' && user?.role === 'ADMIN' && (
        <ChangePasswordSettings username={user.username} />
      )}
      {showDayAndNightDialog && (
        <Modal
          title="Day/Night tariff detected"
          onDismiss={() => setShowDayAndNightDialog(false)}
          actions={
            <button type="button" className="modal__button--primary" onClick={() => setShowDayAndNightDialog(false)}>
              Got it
            </button>
          }
        >
          <p>
            One or more of your tariffs bill electricity at separate Day and Night rates. Octopus
            doesn&rsquo;t report when each rate applies, so enter the valid-from times below under
            &ldquo;Day and Night Tariffs&rdquo; to have usage split correctly. You won&rsquo;t be
            able to leave this page until every tariff is configured.
          </p>
        </Modal>
      )}
    </div>
  )
}

export default App
