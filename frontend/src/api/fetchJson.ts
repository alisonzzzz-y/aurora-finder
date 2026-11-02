import { ApiRequestError } from './requestError.ts'
import { trackSlowRequest } from './slowRequests.ts'

// The deadline includes reading the body, not just receiving HTTP headers.
export async function fetchJson<T>(
  url: string,
  options: RequestInit = {},
  errorMessage = 'The request could not be completed.',
  timeoutMs = 90_000,
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
    finishTracking?.()
    options.signal?.removeEventListener('abort', abortFromCaller)
  }
}
