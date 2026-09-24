import { useEffect, useState } from 'react'
import { getAuroraMap } from '../api/auroraMap'
import type { AuroraMapData } from '../types/auroraMap'

export function useAuroraMapData() {
  const [data, setData] = useState<AuroraMapData | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let controller: AbortController | null = null

    async function refresh() {
      controller?.abort()
      const current = new AbortController()
      controller = current
      try {
        setData(await getAuroraMap(current.signal))
        setError('')
      } catch (cause) {
        if (current.signal.aborted) return
        setData(null)
        setError(cause instanceof Error ? cause.message : 'Aurora forecast data could not be loaded.')
      }
    }

    void refresh()
    const timer = window.setInterval(() => { void refresh() }, 5 * 60 * 1000)
    return () => {
      window.clearInterval(timer)
      controller?.abort()
    }
  }, [])

  return { data, error, loading: data === null && error === '' }
}
