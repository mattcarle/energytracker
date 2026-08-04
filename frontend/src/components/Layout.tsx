import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import './Layout.css'

export default function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()

  return (
    <div className="app-layout">
      <header className="app-header">
        <span className="app-header__title">Energy Tracker</span>
        {user && (
          <div className="app-header__user">
            <span>{user.username}</span>
            <button type="button" onClick={() => logout()}>
              Log out
            </button>
          </div>
        )}
      </header>
      <main>{children}</main>
    </div>
  )
}
