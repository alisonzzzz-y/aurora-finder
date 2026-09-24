import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
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
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const searchRequest = useRef<AbortController | null>(null)
  const normalizedQuery = query.trim()

  useEffect(() => {
    searchRequest.current?.abort()
    searchRequest.current = null

    if (normalizedQuery.length < 2) return

    const controller = new AbortController()
    const timer = window.setTimeout(async () => {
      searchRequest.current = controller
      setState('loading')
      try {
        const locations = await searchLocations(normalizedQuery, controller.signal)
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
    }, 300)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [normalizedQuery])

  useEffect(() => () => searchRequest.current?.abort(), [])

  function updateQuery(value: string) {
    searchRequest.current?.abort()
    searchRequest.current = null
    setQuery(value)
    setResults([])
    setError(null)
    setState('idle')
    setActiveIndex(-1)
  }

  function choose(location: Location) {
    setOpen(false)
    setQuery('')
    onSelect(location)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' && results.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActiveIndex(index => (index + 1) % results.length)
    } else if (event.key === 'ArrowUp' && results.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActiveIndex(index => index <= 0 ? results.length - 1 : index - 1)
    } else if (event.key === 'Enter' && open && activeIndex >= 0) {
      event.preventDefault()
      choose(results[activeIndex])
    } else if (event.key === 'Escape') {
      setOpen(false)
      setActiveIndex(-1)
    }
  }

  const describedBy = state === 'empty'
    ? 'place-search-status'
    : state === 'error'
      ? 'place-search-error'
      : undefined

  return (
    <section
      className="search-panel"
      aria-labelledby="search-title"
      onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setOpen(false)
          setActiveIndex(-1)
        }
      }}
    >
      <div>
        <h2 id="search-title">{t('findPlace')}</h2>
        <p>{t('selectPlaceHelp')}</p>
      </div>
      <div className="search-field-wrap">
        <label htmlFor="place-search" className="sr-only">{t('placeNameLabel')}</label>
        <input
          id="place-search"
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={open && (state === 'results' || state === 'loading')}
          aria-controls="place-search-results"
          aria-activedescendant={activeIndex >= 0 ? `place-option-${results[activeIndex]?.id}` : undefined}
          aria-describedby={describedBy}
          autoComplete="off"
          value={query}
          onChange={event => {
            updateQuery(event.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={t('placePlaceholder')}
          maxLength={80}
          disabled={busy}
        />
        {open && state === 'loading' && <p className="search-status" role="status">{t('loading')}</p>}
        {open && state === 'error' && error !== null && <p id="place-search-error" className="error search-status" role="alert">{localizeError(error, t)}</p>}
        {open && state === 'empty' && <p id="place-search-status" className="hint search-status" role="status">{t('emptySearch')}</p>}
        {open && state === 'results' && <ul id="place-search-results" className="results" role="listbox" aria-label={t('matchingLocations')}>
          {results.map((location, index) => {
            const administrativeArea = [location.region, location.subregion].filter(Boolean).join(', ')
            return <li key={location.id} role="presentation">
              <button
                id={`place-option-${location.id}`}
                type="button"
                role="option"
                aria-selected={activeIndex === index}
                className={activeIndex === index ? 'active' : ''}
                onClick={() => choose(location)}
                disabled={busy}
              >
                <span className="location-option-heading">
                  <strong>{location.name}</strong>
                  <span className="location-country">{location.country}</span>
                </span>
                <span className="location-option-detail">
                  {[administrativeArea, `${location.latitude.toFixed(2)}, ${location.longitude.toFixed(2)}`, location.timezone].filter(Boolean).join(' · ')}
                </span>
              </button>
            </li>
          })}
        </ul>}
      </div>
    </section>
  )
}
