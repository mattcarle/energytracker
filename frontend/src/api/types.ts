export interface MeterPoint {
  id: number
  mpan: string
  isExport: boolean
  meterType: string
  createdAt: string
}

export interface Meter {
  id: number
  serialNumber: string
  meterPointId: number
  createdAt: string
}

export interface Agreement {
  id: number
  tariffCode: string
  validFrom: string
  validTo: string | null
  meterPointId: number
  createdAt: string
}

export interface User {
  id: number
  username: string
  email: string
  createdAt: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface UpdateProfileRequest {
  email?: string
  currentPassword?: string
  newPassword?: string
}
