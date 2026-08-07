import type { Agreement, AuthUser, Meter, MeterPoint, UsageByDayResponse, UserRole } from './types'

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)

  if (method !== 'GET' && method !== 'HEAD') {
    const csrfToken = getCookie('XSRF-TOKEN')
    if (csrfToken) headers.set('X-XSRF-TOKEN', csrfToken)
  }
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...options, headers })

  if (!response.ok) {
    const errorBody = (await response.json().catch(() => null)) as { error?: string } | null
    throw new ApiError(
      errorBody?.error ?? `Request to ${path} failed with status ${response.status}`,
      response.status,
      errorBody?.error,
    )
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function getJson<T>(path: string): Promise<T> {
  return request<T>(path)
}

export function getMeterPoints(): Promise<MeterPoint[]> {
  return getJson('/api/meter-points')
}

export function getMeters(): Promise<Meter[]> {
  return getJson('/api/meters')
}

export function getAgreements(): Promise<Agreement[]> {
  return getJson('/api/agreements')
}

export function getUsageByDay(mpan: string, fromDate: string, toDate: string): Promise<UsageByDayResponse> {
  const params = new URLSearchParams({ mpan, fromDate, toDate })
  return getJson(`/api/usage/by-day?${params.toString()}`)
}

export function getSetupStatus(): Promise<{ setupRequired: boolean }> {
  return getJson('/api/auth/setup-status')
}

export function setupAdmin(password: string): Promise<AuthUser> {
  return request('/api/auth/setup', { method: 'POST', body: JSON.stringify({ password }) })
}

export function login(username: string, password: string): Promise<AuthUser> {
  return request('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
}

export function logout(): Promise<void> {
  return request('/api/auth/logout', { method: 'POST' })
}

export function getCurrentUser(): Promise<AuthUser> {
  return getJson('/api/auth/me')
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return request('/api/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

export function getUsers(): Promise<AuthUser[]> {
  return getJson('/api/users')
}

export function createUser(username: string, initialPassword: string, role: UserRole): Promise<AuthUser> {
  return request('/api/users', { method: 'POST', body: JSON.stringify({ username, initialPassword, role }) })
}

export function deleteUser(id: number): Promise<void> {
  return request(`/api/users/${id}`, { method: 'DELETE' })
}
