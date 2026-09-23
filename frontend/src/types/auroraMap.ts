export type AuroraMapPoint = {
  longitude: number
  latitude: number
  auroraValue: number
}

export type AuroraMapData = {
  observationTime: string
  forecastTime: string
  source: string
  points: AuroraMapPoint[]
}
