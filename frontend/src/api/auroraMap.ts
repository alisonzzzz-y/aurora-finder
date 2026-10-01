import type { AuroraMapData } from '../types/auroraMap'
import { apiUrl } from './apiUrl'
import { fetchJson } from './fetchJson'

export async function getAuroraMap(signal?: AbortSignal): Promise<AuroraMapData> {
  return fetchJson<AuroraMapData>(apiUrl('/api/v1/aurora-map'), { signal }, 'Aurora forecast data is unavailable right now.')
}
