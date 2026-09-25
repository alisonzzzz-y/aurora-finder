import { useEffect, useState } from 'react'
import { getAuroraMap } from '../api/auroraMap'
import { transientRetryDelay } from '../api/requestError'
import type { AuroraMapData } from '../types/auroraMap'

const REFRESH_INTERVAL_MS = 5 * 60 * 1000

export function useAuroraMapData() {
  const [data, setData] = useState<AuroraMapData | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let controller: AbortController | null = null
    let expiryTimer: number | undefined
    let retryTimer: number | undefined
    let retryAttempt = 0

    async function refresh(isRetry = false) {
      if (!isRetry) {
        window.clearTimeout(retryTimer)
        retryTimer = undefined
        retryAttempt = 0
      }
      setError('')
      controller?.abort()
      window.clearTimeout(expiryTimer)
      const current = new AbortController()
      controller = current
      try {
        const response = await getAuroraMap(current.signal)
        if (current.signal.aborted) return

        const forecastDeadline = Date.parse(response.forecastTime)
        const alreadyExpired = response.status === 'CURRENT'
          && Number.isFinite(forecastDeadline)
          && Date.now() > forecastDeadline
        const freshResponse = alreadyExpired
          ? { ...response, status: 'EXPIRED' as const, points: [] }
          : response
        setData(freshResponse)
        setError('')
        retryAttempt = 0

        if (freshResponse.status === 'CURRENT' && Number.isFinite(forecastDeadline)) {
          const delay = forecastDeadline - Date.now() + 100
          if (delay > 0) {
            expiryTimer = window.setTimeout(() => {
              setData(previous => previous?.retrievedAt === freshResponse.retrievedAt
                ? { ...previous, status: 'EXPIRED', points: [] }
                : previous)
              void refresh()
            }, delay)
          }
        }
      } catch (cause) {
        if (current.signal.aborted) return
        setData(null)
        setError(cause instanceof Error ? cause.message : 'Aurora forecast data could not be loaded.')
        const delay = transientRetryDelay(cause, retryAttempt)
        if (delay !== null) {
          retryAttempt += 1
          retryTimer = window.setTimeout(() => {
            retryTimer = undefined
            void refresh(true)
          }, delay)
        }
      }
    }

    void refresh()
    const interval = window.setInterval(() => { void refresh() }, REFRESH_INTERVAL_MS)
    return () => {
      window.clearInterval(interval)
      window.clearTimeout(expiryTimer)
      window.clearTimeout(retryTimer)
      controller?.abort()
    }
  }, [])

  return { data, error, loading: data === null && error === '' }
}
