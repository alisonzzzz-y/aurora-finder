import type { Location } from '../types/location'
import { apiUrl } from './apiUrl'
import { fetchJson } from './fetchJson'

export async function searchLocations(query: string, signal?: AbortSignal): Promise<Location[]> {
  return fetchJson<Location[]>(apiUrl(`/api/v1/locations?q=${encodeURIComponent(query)}`), { signal }, 'Location search is unavailable right now.')
}
