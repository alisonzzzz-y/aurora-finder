import type { KpIndexData } from '../types/kpIndex'
import { apiUrl } from './apiUrl'
import { fetchJson } from './fetchJson'

export async function getKpIndex(signal?: AbortSignal): Promise<KpIndexData> {
  return fetchJson<KpIndexData>(apiUrl('/api/v1/kp-index'), { signal }, 'The global aurora forecast is unavailable right now.')
}
