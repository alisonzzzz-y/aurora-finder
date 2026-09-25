import type { AuroraMapData } from '../types/auroraMap'
import { apiUrl } from './apiUrl'
import { ApiRequestError } from './requestError'

export async function getAuroraMap(signal?: AbortSignal): Promise<AuroraMapData> {
  const response = await fetch(apiUrl('/api/v1/aurora-map'), { signal })
  if (!response.ok) throw new ApiRequestError('Aurora forecast data is unavailable right now.', response.status)
  return (await response.json()) as AuroraMapData
}
