import { apiUrl } from './apiUrl'
import type { ObservationFacts } from '../types/observationFacts'
import { fetchJson } from './fetchJson'

export async function getObservationFacts(locationId: number, signal?: AbortSignal): Promise<ObservationFacts> {
  return fetchJson<ObservationFacts>(apiUrl(`/api/v1/facts/${locationId}`), { signal }, 'The selected location could not be loaded.')
}
