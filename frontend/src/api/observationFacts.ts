import { apiUrl } from './apiUrl'
import type { ObservationFacts } from '../types/observationFacts'

export async function getObservationFacts(locationId: number, signal?: AbortSignal): Promise<ObservationFacts> {
  const response = await fetch(apiUrl(`/api/v1/facts/${locationId}`), { signal })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json() as Promise<ObservationFacts>
}
