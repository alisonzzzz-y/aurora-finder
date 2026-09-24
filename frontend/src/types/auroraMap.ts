export type AuroraMapPoint = {
  longitude: number
  latitude: number
  auroraValue: number
}

export type AuroraMapData = {
  observationTime: string
  forecastTime: string
  retrievedAt: string
  source: string
  points: AuroraMapPoint[]
}
