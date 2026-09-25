import { useEffect, useState } from 'react'
import { getAuroraMap } from '../api/auroraMap'
import type { AuroraMapData } from '../types/auroraMap'

const REFRESH_INTERVAL_MS = 5 * 60 * 1000

export function useAuroraMapData() {
  const [data, setData] = useState<AuroraMapData | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let controller: AbortController | null = null
    let expiryTimer: number | undefined

    async function refresh() {
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
      }
    }

    void refresh()
    const interval = window.setInterval(() => { void refresh() }, REFRESH_INTERVAL_MS)
    return () => {
      window.clearInterval(interval)
      window.clearTimeout(expiryTimer)
      controller?.abort()
    }
  }, [])

  return { data, error, loading: data === null && error === '' }
}
