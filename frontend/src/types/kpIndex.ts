export type AuroraActivityLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export type KpIndexType = 'OBSERVED' | 'ESTIMATED' | 'PREDICTED'

export type KpIndexRecord = {
  periodStart: string
  kp: number
  type: KpIndexType
  noaaScale: string | null
  activityLevel: AuroraActivityLevel
}

export type KpIndexData = {
  retrievedAt: string
  source: string
  records: KpIndexRecord[]
}
