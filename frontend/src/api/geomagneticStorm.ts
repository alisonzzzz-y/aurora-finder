import { apiUrl } from './apiUrl'
import { ApiRequestError } from './requestError'
import type { GeomagneticStormForecast } from '../types/geomagneticStorm'

export async function getGeomagneticStormForecast(signal?: AbortSignal): Promise<GeomagneticStormForecast> {
  const response = await fetch(apiUrl('/api/v1/geomagnetic-storm-forecast'), { signal })
  if (!response.ok) throw new ApiRequestError('The geomagnetic forecast is unavailable right now.', response.status)
  return (await response.json()) as GeomagneticStormForecast
}
