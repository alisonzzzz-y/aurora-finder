import { apiUrl } from './apiUrl'
import { fetchJson } from './fetchJson'
import { ApiRequestError } from './requestError'

export type PushConfig = { enabled: boolean; publicKey?: string | null }

export function getPushConfig(signal?: AbortSignal) {
  return fetchJson<PushConfig>(apiUrl('/api/v1/push/config'), { signal }, 'Background alerts are unavailable.')
}

async function postNoContent(path: string, body: unknown) {
  const response = await fetch(apiUrl(path), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(15_000),
  })
  if (!response.ok) throw new ApiRequestError('Background alerts could not be updated.', response.status)
}

export function saveSubscription(subscription: PushSubscription, language: 'en' | 'zh') {
  const json = subscription.toJSON()
  return postNoContent('/api/v1/push/subscriptions', { endpoint: json.endpoint, keys: json.keys, language })
}

export function removeSubscription(endpoint: string) {
  return postNoContent('/api/v1/push/unsubscribe', { endpoint })
}

/** Converts the server's base64url VAPID key to the bytes pushManager.subscribe expects. */
export function applicationServerKey(base64Url: string): Uint8Array<ArrayBuffer> {
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(base64Url.length / 4) * 4, '=')
  const binary = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(binary.length))
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
  return bytes
}
