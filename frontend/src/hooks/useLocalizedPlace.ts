import { useEffect, useMemo, useState } from 'react'
import { loadPlaceNames } from '../api/locations'
import { knownPlaceNames, type PlaceNames } from '../api/placeNames'
import { useI18n } from '../i18n'
import type { Location } from '../types/location'

/** The place with its names in the page language. It keeps the names it has until better ones arrive. */
export function useLocalizedPlace<T extends Location | null>(place: T): T {
  const { language } = useI18n()
  const placeId = place?.id
  const [loaded, setLoaded] = useState<{ id: number; language: string; names: PlaceNames } | null>(null)

  useEffect(() => {
    if (placeId === undefined || knownPlaceNames(placeId, language)) return
    let active = true
    loadPlaceNames(placeId, language)
      .then(names => { if (active) setLoaded({ id: placeId, language, names }) })
      .catch(() => {})
    return () => { active = false }
  }, [placeId, language])

  return useMemo(() => {
    if (!place) return place
    const names = knownPlaceNames(place.id, language)
      ?? (loaded?.id === place.id && loaded.language === language ? loaded.names : null)
    return names ? { ...place, ...names } : place
  }, [place, language, loaded])
}
