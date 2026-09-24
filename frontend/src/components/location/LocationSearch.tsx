import { useEffect, useRef, useState, type FormEvent } from 'react'
import { searchLocations } from '../../api/locations'
import type { Location } from '../../types/location'
import { localizeError, useI18n } from '../../i18n'
import './LocationSearch.css'

type Props = {
  busy: boolean
  onSelect: (location: Location) => void
}

type SearchState = 'idle' | 'loading' | 'results' | 'empty' | 'error'

export function LocationSearch({ busy, onSelect }: Props) {
  const { t } = useI18n()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Location[]>([])
  const [error, setError] = useState<unknown>(null)
  const [state, setState] = useState<SearchState>('idle')
  const searchRequest = useRef<AbortController | null>(null)

  useEffect(() => () => searchRequest.current?.abort(), [])

  function updateQuery(value: string) {
    searchRequest.current?.abort()
    searchRequest.current = null
    setQuery(value)
    setResults([])
    setError('')
    setState('idle')
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cleanedQuery = query.trim()
    if (cleanedQuery.length < 2) {
      setError(new Error('queryTooShort'))
      return
    }
    setError(null)
    setResults([])
    setState('loading')
    searchRequest.current?.abort()
    const controller = new AbortController()
    searchRequest.current = controller
    try {
      const locations = await searchLocations(cleanedQuery, controller.signal)
      if (controller.signal.aborted || searchRequest.current !== controller) return
      setResults(locations)
      setState(locations.length === 0 ? 'empty' : 'results')
    } catch (cause) {
      if (controller.signal.aborted || searchRequest.current !== controller) return
      setError(cause)
      setState('error')
    } finally {
      if (searchRequest.current === controller) searchRequest.current = null
    }
  }

  return (
    <section className="search-panel" aria-labelledby="search-title">
      <div>
        <h2 id="search-title">{t('findPlace')}</h2>
        <p>{t('selectPlaceHelp')}</p>
      </div>
      <form onSubmit={search} className="search-form">
        <label htmlFor="place-search" className="sr-only">{t('placeNameLabel')}</label>
        <input id="place-search" value={query} onChange={event => updateQuery(event.target.value)} placeholder={t('placePlaceholder')} minLength={2} maxLength={80} required disabled={busy} />
        <button disabled={busy || state === 'loading'} type="submit">{state === 'loading' ? t('loading') : t('search')}</button>
      </form>
      {error !== null && <p className="error" role="alert">{error instanceof Error && error.message === 'queryTooShort' ? t('queryTooShort') : localizeError(error, t)}</p>}
      {state === 'results' && <ul className="results" aria-label={t('matchingLocations')}>{results.map(location => (
        <li key={location.id}><button type="button" onClick={() => onSelect(location)} disabled={busy}>
          <strong>{location.name}</strong><span>
            {[location.subregion, location.region, location.country].filter(Boolean).join(', ')}
            <br />{location.latitude.toFixed(3)}, {location.longitude.toFixed(3)} · {location.timezone}
          </span>
        </button></li>
      ))}</ul>}
      {state === 'empty' && <p className="hint">{t('emptySearch')}</p>}
    </section>
  )
}
