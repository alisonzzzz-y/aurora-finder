import { apiUrl } from './apiUrl'
import type { GeomagneticStormForecast } from '../types/geomagneticStorm'
import { fetchJson } from './fetchJson'

export async function getGeomagneticStormForecast(signal?: AbortSignal): Promise<GeomagneticStormForecast> {
  return fetchJson<GeomagneticStormForecast>(apiUrl('/api/v1/geomagnetic-storm-forecast'), { signal }, 'The geomagnetic forecast is unavailable right now.')
}
