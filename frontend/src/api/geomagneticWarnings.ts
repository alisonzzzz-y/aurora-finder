import { apiUrl } from './apiUrl'
import type { GeomagneticWarnings } from '../types/geomagneticWarnings'
import { fetchJson } from './fetchJson'

export async function getGeomagneticWarnings(signal?: AbortSignal): Promise<GeomagneticWarnings> {
  return fetchJson<GeomagneticWarnings>(apiUrl('/api/v1/geomagnetic-warnings'), { signal }, 'Current geomagnetic warnings are unavailable.')
}
