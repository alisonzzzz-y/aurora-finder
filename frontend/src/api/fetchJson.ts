import { ApiRequestError } from './requestError.ts'
import { trackSlowRequest } from './slowRequests.ts'

// A sleeping free-tier backend took 115 seconds to answer when measured, and answers once awake.
const DEFAULT_TIMEOUT_MS = 150_000
// Callers retry 10 seconds after a timeout and the new request counts as slow 8 seconds later.
const SLOW_NOTICE_AFTER_TIMEOUT_MS = 20_000

// The deadline includes reading the body, not just receiving HTTP headers.
export async function fetchJson<T>(
  url: string,
  options: RequestInit = {},
  errorMessage = 'The request could not be completed.',
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const controller = new AbortController()
  const abortFromCaller = () => controller.abort(options.signal?.reason)
  if (options.signal?.aborted) abortFromCaller()
  else options.signal?.addEventListener('abort', abortFromCaller, { once: true })
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)
  // Assistant answers are POSTs that are slow for their own reasons, and map names come from another service.
  const finishTracking = url.includes('/api/v1/') && (options.method ?? 'GET') === 'GET' ? trackSlowRequest() : undefined
  try {
    const response = await fetch(url, { ...options, signal: controller.signal })
    if (!response.ok) {
      const problem: unknown = await response.json().catch(() => null)
      const code = problem && typeof problem === 'object' && 'code' in problem
        && typeof problem.code === 'string' ? problem.code : undefined
      throw new ApiRequestError(errorMessage, response.status, code)
    }
    return await response.json() as T
  } catch (error) {
    if (timedOut && !options.signal?.aborted) throw new ApiRequestError('The request timed out.', 408)
    throw error
  } finally {
    clearTimeout(timer)
    // The server may still be waking, so a timeout should not take the notice away before the retry.
    if (timedOut && !options.signal?.aborted && finishTracking) setTimeout(finishTracking, SLOW_NOTICE_AFTER_TIMEOUT_MS)
    else finishTracking?.()
    options.signal?.removeEventListener('abort', abortFromCaller)
  }
}
