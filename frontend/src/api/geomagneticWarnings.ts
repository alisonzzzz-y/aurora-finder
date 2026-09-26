import { apiUrl } from './apiUrl'
import { ApiRequestError } from './requestError'
import type { GeomagneticWarnings } from '../types/geomagneticWarnings'

export async function getGeomagneticWarnings(signal?: AbortSignal): Promise<GeomagneticWarnings> {
  const response = await fetch(apiUrl('/api/v1/geomagnetic-warnings'), { signal })
  if (!response.ok) throw new ApiRequestError('Current geomagnetic warnings are unavailable.', response.status)
  return (await response.json()) as GeomagneticWarnings
}
