import type { AuroraMapData } from '../types/auroraMap'
import { apiUrl } from './apiUrl'

export async function getAuroraMap(signal?: AbortSignal): Promise<AuroraMapData> {
  const response = await fetch(apiUrl('/api/v1/aurora-map'), { signal })
  if (!response.ok) throw new Error('Aurora forecast data is unavailable right now.')
  return (await response.json()) as AuroraMapData
}
