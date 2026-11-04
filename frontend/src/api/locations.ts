import type { Location } from '../types/location'
import { apiUrl } from './apiUrl'
import { fetchJson } from './fetchJson'
import { displayNames, knownPlaceNames, rememberPlaceNames, storePlaceNames, type PageLanguage, type PlaceNames } from './placeNames'

export async function searchLocations(query: string, language: PageLanguage, signal?: AbortSignal): Promise<Location[]> {
  const found = await fetchJson<Location[]>(apiUrl(`/api/v1/locations?q=${encodeURIComponent(query)}&language=${language}`), { signal }, 'Location search is unavailable right now.')
  return found.map(location => {
    const named = displayNames(location, language)
    rememberPlaceNames(named.id, language, named)
    return named
  })
}

const requests = new Map<string, Promise<PlaceNames>>()

export function loadPlaceNames(id: number, language: PageLanguage): Promise<PlaceNames> {
  const known = knownPlaceNames(id, language)
  if (known) return Promise.resolve(known)
  const requestKey = `${id}:${language}`
  const pending = requests.get(requestKey)
  if (pending) return pending
  const request = fetchJson<Location>(apiUrl(`/api/v1/locations/${id}?language=${language}`), {}, 'The selected location could not be loaded.')
    .then(location => {
      const named = displayNames(location, language)
      storePlaceNames(id, language, named)
      return named
    })
    .finally(() => { requests.delete(requestKey) })
  requests.set(requestKey, request)
  return request
}
