export type LocalAuroraActivity = {
  status: 'CURRENT' | 'EXPIRED'
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'INSUFFICIENT_DATA'
  modelValue: number | null
  gridLongitude: number | null
  gridLatitude: number | null
  observationTime: string
  forecastTime: string
  retrievedAt: string
  source: string
  ruleVersion: string
}
