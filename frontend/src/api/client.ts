import type { Agreement, Meter, MeterPoint } from './types'

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) {
    throw new Error(`Request to ${path} failed with status ${response.status}`)
  }
  return response.json() as Promise<T>
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
