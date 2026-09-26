export type AuroraActivityLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export type KpIndexType = 'OBSERVED' | 'ESTIMATED' | 'PREDICTED'

export type NoaaGeomagneticStormScale = 'BELOW_G1' | 'G1' | 'G2' | 'G3' | 'G4' | 'G5'

export type KpIndexRecord = {
  periodStart: string
  kp: number
  type: KpIndexType
  noaaScale: string | null
  activityLevel: AuroraActivityLevel
  geomagneticStormScale?: NoaaGeomagneticStormScale
}

export type KpIndexData = {
  retrievedAt: string
  source: string
  records: KpIndexRecord[]
}
