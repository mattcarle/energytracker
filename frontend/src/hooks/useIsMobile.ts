import { useEffect, useState } from 'react'

const MOBILE_QUERY = '(max-width: 640px)'

// Single source of truth for "mobile" across the app (nav layout, which Usage-page views are
// offered) - real JS state rather than pure CSS because some of what depends on it (e.g. the
// table/chart/insights toggle-button "don't hide the last visible view" safeguard) needs to
// know at render time whether the table is even an option, not just whether it's painted.
export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(() => window.matchMedia(MOBILE_QUERY).matches)

  useEffect(() => {
    const mql = window.matchMedia(MOBILE_QUERY)
    const handleChange = (e: MediaQueryListEvent) => setIsMobile(e.matches)
    mql.addEventListener('change', handleChange)
    return () => mql.removeEventListener('change', handleChange)
  }, [])

  return isMobile
}
