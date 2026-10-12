import { useEffect, useMemo, useRef, useState } from 'react'
import { fetchJson } from '../api/fetchJson'
import type { AuroraMapPoint } from '../types/auroraMap'
import { activityAreaName, activityPointKey, type ReverseGeocodingResult } from '../components/aurora/activityAreaNames'

// Keep only a small temporary cache in this page; never persist geocoding results.
export function useActivityAreaNames(points: AuroraMapPoint[], language: 'zh' | 'en') {
  const cache = useRef(new Map<string, { name: string | null; expires: number }>())
  const [names, setNames] = useState<Record<string, string>>({})
  const key = import.meta.env.VITE_MAPTILER_KEY?.trim()
  useEffect(() => {
    if (!key || points.length === 0) return
    const controller = new AbortController()
    function publish(id: string, name: string) {
      setNames(previous => {
        if (previous[id] === name) return previous
        return Object.fromEntries([...Object.entries(previous).filter(([stored]) => stored !== id), [id, name]].slice(-64))
      })
    }
    async function lookup(point: AuroraMapPoint) {
      const id = `${language}:${activityPointKey(point)}`
      const cached = cache.current.get(id)
      if (cached && cached.expires > Date.now()) {
        if (cached.name) publish(id, cached.name)
        return
      }
      const url = new URL(`https://api.maptiler.com/geocoding/${activityPointKey(point)}.json`)
      url.search = new URLSearchParams({ key: key!, language, types: 'subregion,region,country,continental_marine' }).toString()
      let name: string | null = null
      try {
        const response = await fetchJson<ReverseGeocodingResult>(url.toString(), { signal: controller.signal }, 'Place name unavailable.', 8000)
        name = activityAreaName(response, language)
      } catch { /* The model marker remains usable when names cannot be loaded. */ }
      if (controller.signal.aborted) return
      cache.current.set(id, { name, expires: Date.now() + (name ? 1800000 : 60000) })
      if (cache.current.size > 64) cache.current.delete(cache.current.keys().next().value!)
      if (name) publish(id, name)
    }
    void Promise.all(points.map(lookup))
    return () => controller.abort()
  }, [points, language, key])
  // Key by coordinates and language so late responses cannot label a newer point.
  return useMemo(() => Object.fromEntries(points.map(point => [activityPointKey(point), names[`${language}:${activityPointKey(point)}`] ?? ''])), [points, names, language])
}
