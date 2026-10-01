import { ApiRequestError } from './requestError.ts'

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
  try {
    const response = await fetch(url, { ...options, signal: controller.signal })
    if (!response.ok) throw new ApiRequestError(errorMessage, response.status)
    return await response.json() as T
  } catch (error) {
    if (timedOut && !options.signal?.aborted) throw new ApiRequestError('The request timed out.', 408)
    throw error
  } finally {
    clearTimeout(timer)
    options.signal?.removeEventListener('abort', abortFromCaller)
  }
}
