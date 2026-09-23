import { useState, type FormEvent } from 'react'
import './App.css'

type Location = {
  id: number
  name: string
  region: string
  country: string
  latitude: number
  longitude: number
  timezone: string
}

type NightOutlook = {
  localDate: string
  utcOffsetAtStart: string
  level: 'INSUFFICIENT_DATA'
  reason: string
}

type Outlook = {
  location: Location
  generatedAtUtc: string
  ruleStatus: 'NOT_VALIDATED'
  nights: NightOutlook[]
}

type SearchState = 'idle' | 'loading' | 'results' | 'empty' | 'selected' | 'error'

function formatLocalTimestamp(instant: string, timezone: string) {
  const date = new Date(instant)
  const localTime = new Intl.DateTimeFormat('en', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: timezone,
  }).format(date)
  const offset = new Intl.DateTimeFormat('en', {
    hour: '2-digit',
    timeZone: timezone,
    timeZoneName: 'longOffset',
  }).formatToParts(date).find(part => part.type === 'timeZoneName')?.value ?? 'GMT'
  return localTime + ' (' + offset.replace(/^GMT/, 'UTC') + ')'
}

function App() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Location[]>([])
  const [outlook, setOutlook] = useState<Outlook | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [searchState, setSearchState] = useState<SearchState>('idle')

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (query.trim().length < 2) return
    setBusy(true)
    setError('')
    setOutlook(null)
    setResults([])
    setSearchState('loading')
    try {
      const response = await fetch(`/api/v1/locations?q=${encodeURIComponent(query.trim())}`)
      if (!response.ok) throw new Error('Location search is unavailable right now.')
      const locations = (await response.json()) as Location[]
      setResults(locations)
      setSearchState(locations.length === 0 ? 'empty' : 'results')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Location search failed.')
      setSearchState('error')
    } finally {
      setBusy(false)
    }
  }

  async function selectLocation(location: Location) {
    setBusy(true)
    setError('')
    try {
      const response = await fetch(`/api/v1/outlooks/${location.id}`)
      if (!response.ok) throw new Error('The selected location could not be loaded.')
      setOutlook((await response.json()) as Outlook)
      setResults([])
      setSearchState('selected')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The outlook could not be loaded.')
      setSearchState('results')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page-shell">
      <header className="site-header">
        <div className="brand"><span className="brand-mark">✦</span> Aurora Outlook</div>
        <span className="status-pill">Project foundation</span>
      </header>

      <main>
        <section className="intro">
          <p className="eyebrow">PLAN A NIGHT OUTSIDE</p>
          <h1>A clearer view of the northern and southern lights.</h1>
          <p className="intro-copy">Choose a place to see its next three local nights. Forecast data and viewing rules are still being validated, so this version does not rate the chances of seeing an aurora.</p>
        </section>

        <section className="search-panel" aria-labelledby="search-title">
          <div>
            <h2 id="search-title">Find a place</h2>
            <p>Select a result to confirm the place and its time zone.</p>
          </div>
          <form onSubmit={search} className="search-form">
            <label htmlFor="place-search" className="sr-only">City or place name</label>
            <input id="place-search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Try Dublin, Tromsø, or Dunedin" minLength={2} maxLength={80} required />
            <button disabled={busy} type="submit">{busy ? 'Loading…' : 'Search'}</button>
          </form>
          {error && <p className="error" role="alert">{error}</p>}
          {results.length > 0 && <ul className="results" aria-label="Matching locations">{results.map(location => (
            <li key={location.id}><button type="button" onClick={() => selectLocation(location)} disabled={busy}>
              <strong>{location.name}</strong><span>{[location.region, location.country].filter(Boolean).join(', ')} · {location.timezone}</span>
            </button></li>
          ))}</ul>}
          {searchState === 'empty' && <p className="hint">No matching places found. Try another name or spelling.</p>}
        </section>

        {outlook && <section className="outlook" aria-labelledby="outlook-title">
          <div className="section-heading"><div><p className="eyebrow">LOCAL OUTLOOK</p><h2 id="outlook-title">{outlook.location.name}, {outlook.location.country}</h2></div><span>{outlook.location.timezone}</span></div>
          <div className="night-grid">{outlook.nights.map((night, index) => <article className="night-card" key={night.localDate}>
            <p className="night-index">{index === 0 ? 'Tonight' : `Night ${index + 1}`}</p>
            <h3>{new Intl.DateTimeFormat('en', { weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(night.localDate + 'T12:00:00Z'))} <span className="utc-offset">(UTC{night.utcOffsetAtStart})</span></h3>
            <span className="unknown-badge">Insufficient data</span>
            <p>{night.reason}</p>
          </article>)}</div>
          <p className="timestamp">Response generated {formatLocalTimestamp(outlook.generatedAtUtc, outlook.location.timezone)}. Dates and times follow the selected place's local time.</p>
        </section>}

        <section className="foundation-grid" aria-label="Upcoming features and sources">
          <article className="feature-card"><p className="eyebrow">AURORA MAP</p><h2>Short-range activity</h2><p>The NOAA OVATION layer will appear here only when its forecast time and data freshness have been checked. The model's aurora area is not a ground visibility boundary.</p><span className="coming-soon">Awaiting integration</span></article>
          <article className="feature-card"><p className="eyebrow">SOURCE NOTES</p><h2>Know what supports the outlook</h2><p>NOAA aurora and Kp forecasts, MET Norway cloud forecasts, and local darkness need separate timestamps and coverage checks. Missing data must stay visible.</p><div className="source-links"><a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA OVATION ↗</a><a href="https://docs.api.met.no/doc/locationforecast/datamodel.html" target="_blank" rel="noreferrer">MET Norway ↗</a><a href="https://open-meteo.com/en/docs/geocoding-api" target="_blank" rel="noreferrer">Open-Meteo geocoding ↗</a></div></article>
        </section>
      </main>
      <footer>Location search uses Open-Meteo geocoding data under CC BY 4.0. Aurora viewing advice is not available in this foundation release.</footer>
      <button className="agent-button" type="button" disabled title="AI questions will be available after the shared facts service is validated">Ask about a night <span>✦</span></button>
    </div>
  )
}

export default App
