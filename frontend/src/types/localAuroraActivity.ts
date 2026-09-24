export type LocalAuroraActivity = {
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'INSUFFICIENT_DATA'
  modelValue: number
  gridLongitude: number
  gridLatitude: number
  observationTime: string
  forecastTime: string
  source: string
  ruleVersion: string
}
