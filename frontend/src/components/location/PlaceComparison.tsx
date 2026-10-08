import { useEffect, useState } from 'react'
import { getObservationFacts } from '../../api/observationFacts'
import { localizeError, useI18n } from '../../i18n'
import type { Location } from '../../types/location'
import type { ObservationFacts } from '../../types/observationFacts'
import { ViewingConditionsSummary } from '../outlook/ViewingConditionsSummary'
import { InfoHint } from '../InfoHint'
import { LocationSearch } from './LocationSearch'
import './PlaceComparison.css'

// These IDs were confirmed through the same location provider used by search.
const candidates = [{ id: 3413829, name: 'Reykjavik, Iceland' }, { id: 5861897, name: 'Fairbanks, Alaska' }]
type Candidate = { id: number; name: string }

export function PlaceComparison({ onSelect }: { onSelect: (location: Location) => void }) {
  const { t } = useI18n()
  const [places, setPlaces] = useState<Candidate[]>(candidates)
  const [revision, setRevision] = useState(0)
  const [limit, setLimit] = useState(false)
  return <section className="place-comparison" aria-labelledby="place-comparison-title">
    <header className="comparison-heading"><h2 id="place-comparison-title">{t('compareTitle')}<InfoHint>{t('compareNote')}</InfoHint></h2><button className="comparison-action" type="button" onClick={() => setRevision(value => value + 1)}>{t('compareRefresh')}</button></header>
    <LocationSearch busy={false} title={t('compareAdd')} buttonLabel={t('compareButton')} onSelect={location => {
      if (places.some(place => place.id === location.id)) return
      if (places.length >= 3) { setLimit(true); return }
      setLimit(false)
      setPlaces(previous => [...previous, { id: location.id, name: `${location.name}, ${location.country}` }])
    }} />
    {limit && <p role="status">{t('compareLimit')}</p>}
    <div className="comparison-grid">{places.map(place => <ComparisonRow key={place.id} place={place} revision={revision} onSelect={onSelect} onRemove={() => {
      setPlaces(previous => previous.filter(item => item.id !== place.id)); setLimit(false)
    }} />)}</div>
    <p className="comparison-credit">{t('compareSources')} <a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA</a> · <a href="https://api.met.no/weatherapi/locationforecast/2.0/documentation" target="_blank" rel="noreferrer">MET Norway</a> · <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a></p>
  </section>
}

function ComparisonRow({ place, revision, onSelect, onRemove }: {
  place: Candidate; revision: number; onSelect: (location: Location) => void; onRemove: () => void
}) {
  const { t } = useI18n()
  const [facts, setFacts] = useState<ObservationFacts | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let controller: AbortController | undefined
    async function load() {
      controller?.abort()
      const request = new AbortController(); controller = request
      setLoading(true); setError(null)
      try {
        const response = await getObservationFacts(place.id, request.signal)
        if (!request.signal.aborted) setFacts(response)
      } catch (cause) {
        if (!request.signal.aborted) { setFacts(null); setError(cause) }
      } finally { if (!request.signal.aborted) setLoading(false) }
    }
    void load()
    const timer = window.setInterval(() => { void load() }, 300000)
    return () => { controller?.abort(); window.clearInterval(timer) }
  }, [place.id, revision])
  return <article className="comparison-row">
    <header><h4>{place.name}</h4><button className="comparison-remove" type="button" aria-label={`${t('compareRemove')} ${place.name}`} onClick={onRemove}>{t('compareRemove')}</button></header>
    {loading && <p role="status">{t('loading')}</p>}
    {error !== null && <p role="status">{localizeError(error, t)}</p>}
    {!loading && facts && <><ViewingConditionsSummary facts={facts} /><button className="comparison-action comparison-map" type="button" onClick={() => onSelect(facts.outlook.location)}>{t('compareMap')}</button></>}
  </article>
}
