import { useState, type FormEvent } from 'react'
import { searchLocations } from '../../api/locations'
import type { Location } from '../../types/location'
import './LocationSearch.css'

type Props = {
  busy: boolean
  onSelect: (location: Location) => void
}

type SearchState = 'idle' | 'loading' | 'results' | 'empty' | 'error'

export function LocationSearch({ busy, onSelect }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Location[]>([])
  const [error, setError] = useState('')
  const [state, setState] = useState<SearchState>('idle')

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cleanedQuery = query.trim()
    if (cleanedQuery.length < 2) return
    setError('')
    setResults([])
    setState('loading')
    try {
      const locations = await searchLocations(cleanedQuery)
      setResults(locations)
      setState(locations.length === 0 ? 'empty' : 'results')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Location search failed.')
      setState('error')
    }
  }

  return (
    <section className="search-panel" aria-labelledby="search-title">
      <div>
        <h2 id="search-title">Find a place</h2>
        <p>Select a result to confirm the place and its time zone.</p>
      </div>
      <form onSubmit={search} className="search-form">
        <label htmlFor="place-search" className="sr-only">City or place name</label>
        <input id="place-search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Try Dublin, Tromsø, or Dunedin" minLength={2} maxLength={80} required />
        <button disabled={busy || state === 'loading'} type="submit">{state === 'loading' ? 'Loading…' : 'Search'}</button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {state === 'results' && <ul className="results" aria-label="Matching locations">{results.map(location => (
        <li key={location.id}><button type="button" onClick={() => onSelect(location)} disabled={busy}>
          <strong>{location.name}</strong><span>
            {[location.subregion, location.region, location.country].filter(Boolean).join(', ')}
            <br />{location.latitude.toFixed(3)}, {location.longitude.toFixed(3)} · {location.timezone}
          </span>
        </button></li>
      ))}</ul>}
      {state === 'empty' && <p className="hint">No matching places found. Try another name or spelling.</p>}
    </section>
  )
}
