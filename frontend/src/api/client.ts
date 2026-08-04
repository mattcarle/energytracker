import type {
  Agreement,
  LoginRequest,
  Meter,
  MeterPoint,
  RegisterRequest,
  UpdateProfileRequest,
  User,
} from './types'

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

let unauthorizedHandler: (() => void) | null = null

/** Called whenever a request comes back 401, e.g. so the app can drop its cached user and redirect to /login. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)

  if (options.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const csrfToken = getCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken)
    }
  }

  const response = await fetch(path, { ...options, headers, credentials: 'include' })

  if (!response.ok) {
    if (response.status === 401) {
      unauthorizedHandler?.()
    }
    let message = `Request to ${path} failed with status ${response.status}`
    try {
      const data = await response.json()
      if (data?.error) message = data.error
    } catch {
      // response had no JSON body; fall back to the generic message
    }
    throw new ApiError(message, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function getMeterPoints(): Promise<MeterPoint[]> {
  return request('/api/meter-points')
}

export function getMeters(): Promise<Meter[]> {
  return request('/api/meters')
}

export function getAgreements(): Promise<Agreement[]> {
  return request('/api/agreements')
}

export function register(data: RegisterRequest): Promise<User> {
  return request('/api/auth/register', { method: 'POST', body: JSON.stringify(data) })
}

export function login(data: LoginRequest): Promise<User> {
  return request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) })
}

export function logout(): Promise<void> {
  return request('/api/auth/logout', { method: 'POST' })
}

export function getCurrentUser(): Promise<User> {
  return request('/api/users/me')
}

export function updateCurrentUser(data: UpdateProfileRequest): Promise<User> {
  return request('/api/users/me', { method: 'PUT', body: JSON.stringify(data) })
}
