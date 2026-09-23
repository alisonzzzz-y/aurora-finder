import type { AuroraMapData } from '../types/auroraMap'

export async function getAuroraMap(signal?: AbortSignal): Promise<AuroraMapData> {
  const response = await fetch('/api/v1/aurora-map', { signal })
  if (!response.ok) throw new Error('Aurora forecast data is unavailable right now.')
  return (await response.json()) as AuroraMapData
}
