export class ApiRequestError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

const RETRY_DELAYS_MS = [10_000, 30_000, 60_000]

export function transientRetryDelay(error: unknown, attempt: number): number | null {
  const transientHttpError = error instanceof ApiRequestError
    && (error.status === 408 || error.status >= 500)
  const networkError = error instanceof TypeError
  if (!transientHttpError && !networkError) return null
  return RETRY_DELAYS_MS[attempt] ?? null
}
