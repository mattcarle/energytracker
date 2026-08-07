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

export interface UsageDayAggregate {
  mpan: string
  meterType: string
  isExport: boolean
  usageDate: string
  intervalCount: number
  kwh: number
  cost: number
  avgRate: number | null
}

export interface UsageByDayResponse {
  days: UsageDayAggregate[]
  totals: {
    intervalCount: number
    kwh: number
    cost: number
    avgRate: number | null
  }
}

export type UserRole = 'ADMIN' | 'USER'

export interface AuthUser {
  id: number
  username: string
  role: UserRole
  mustChangePassword: boolean
  createdAt: string
}
