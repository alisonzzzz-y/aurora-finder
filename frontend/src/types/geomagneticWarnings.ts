export type GeomagneticWarning = {
  productId: string
  expectedKIndex: number
  noaaScale: string | null
  validFrom: string
  validTo: string
}

export type GeomagneticStormWatchDay = {
  date: string
  noaaScale: string | null
}

export type GeomagneticWarnings = {
  retrievedAt: string
  source: string
  warnings: GeomagneticWarning[]
  stormWatchDays?: GeomagneticStormWatchDay[]
}
