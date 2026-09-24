export type WeatherCloudPoint = {
  validAt: string
  cloudCoverPercent: number | null
}

export type WeatherForecast = {
  retrievedAt: string
  expiresAt: string
  source: string
  requestedLatitude: number
  requestedLongitude: number
  cloudForecast: WeatherCloudPoint[]
}
