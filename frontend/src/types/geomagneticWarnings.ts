export type GeomagneticWarning = {
  productId: string
  expectedKIndex: number
  noaaScale: string | null
  validFrom: string
  validTo: string
}

export type GeomagneticWarnings = {
  retrievedAt: string
  source: string
  warnings: GeomagneticWarning[]
}
