import { useState } from 'react'
import AccountDetails from './pages/AccountDetails'
import UsageByMonth from './pages/UsageByMonth'
import './App.css'

type Page = 'account' | 'usage'

function App() {
  const [page, setPage] = useState<Page>('account')

  return (
    <div className="app-shell">
      <nav className="app-nav">
        <span className="app-nav__title">Energy Tracker</span>
        <div className="app-nav__links">
          <button
            type="button"
            className={page === 'account' ? 'active' : ''}
            onClick={() => setPage('account')}
          >
            Account details
          </button>
          <button
            type="button"
            className={page === 'usage' ? 'active' : ''}
            onClick={() => setPage('usage')}
          >
            Usage
          </button>
        </div>
      </nav>
      {page === 'account' ? <AccountDetails /> : <UsageByMonth />}
    </div>
  )
}

export default App
