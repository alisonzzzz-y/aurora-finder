export type GeomagneticStormDay = {
  date: string
  activeChancePercent: number
  minorStormChancePercent: number
  moderateStormChancePercent: number
  strongExtremeStormChancePercent: number
}

export type GeomagneticStormForecast = {
  retrievedAt: string
  issuedAt: string
  source: string
  days: GeomagneticStormDay[]
}
