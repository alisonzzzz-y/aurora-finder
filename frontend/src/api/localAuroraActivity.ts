import type { LocalAuroraActivity } from '../types/localAuroraActivity'
import { apiUrl } from './apiUrl'

export async function getLocalAuroraActivity(latitude: number, longitude: number, signal?: AbortSignal) {
  const query = new URLSearchParams({ latitude: String(latitude), longitude: String(longitude) })
  const response = await fetch(apiUrl(`/api/v1/aurora-activity?${query}`), { signal })
  if (!response.ok) throw new Error('Local aurora activity is unavailable right now.')
  return (await response.json()) as LocalAuroraActivity
}
