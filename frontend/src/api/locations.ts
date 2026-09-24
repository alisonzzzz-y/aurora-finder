import type { Location } from '../types/location'

export async function searchLocations(query: string, signal?: AbortSignal): Promise<Location[]> {
  const response = await fetch(`/api/v1/locations?q=${encodeURIComponent(query)}`, { signal })
  if (!response.ok) throw new Error('Location search is unavailable right now.')
  return (await response.json()) as Location[]
}
