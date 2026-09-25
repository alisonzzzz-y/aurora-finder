import type { KpIndexData } from '../types/kpIndex'
import { apiUrl } from './apiUrl'
import { ApiRequestError } from './requestError'

export async function getKpIndex(signal?: AbortSignal): Promise<KpIndexData> {
  const response = await fetch(apiUrl('/api/v1/kp-index'), { signal })
  if (!response.ok) throw new ApiRequestError('The global aurora forecast is unavailable right now.', response.status)
  return (await response.json()) as KpIndexData
}
