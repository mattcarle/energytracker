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
