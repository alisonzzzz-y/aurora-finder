import type { AuroraMapPoint } from '../../types/auroraMap'

type NamedFeature = {
  text?: string
  place_type?: string[]
  context?: { id?: string; text?: string }[]
}
export type ReverseGeocodingResult = { features?: NamedFeature[] }

export function activityPointKey(point: Pick<AuroraMapPoint, 'latitude' | 'longitude'>) {
  return `${point.longitude},${point.latitude}`
}

export function activityCoordinates(point: Pick<AuroraMapPoint, 'latitude' | 'longitude'>) {
  const coordinate = (value: number, positive: string, negative: string) =>
    `${Math.abs(value).toFixed(1)}° ${value < 0 ? negative : positive}`
  return `${coordinate(point.latitude, 'N', 'S')} · ${coordinate(point.longitude, 'E', 'W')}`
}

// Reverse geocoding returns the containing administrative/marine layers. Do not
// infer a nearby city from a distant feature centre, especially over the ocean.
export function activityAreaName(data: ReverseGeocodingResult, language: 'zh' | 'en'): string | null {
  if (!Array.isArray(data.features)) return null
  const features = data.features.filter(f => f && typeof f.text === 'string' && f.text.trim())
  const layer = (type: string) => features.find(f => f.place_type?.includes(type))
  const country = layer('country')
  const region = layer('subregion') ?? layer('region')
  const clean = (name: string | undefined) => name?.trim().split('/')[0].trim() ?? ''
  const countryName = clean(country?.text ?? region?.context?.find(c => c.id?.startsWith('country.'))?.text)
  if (countryName) {
    const regionName = clean(region?.text)
    return regionName && regionName !== countryName
      ? `${countryName} · ${regionName}${language === 'zh' ? '一带' : ' area'}`
      : countryName
  }
  // Continental/marine also covers Antarctica and unnamed polar land areas.
  return clean(layer('continental_marine')?.text) || null
}
