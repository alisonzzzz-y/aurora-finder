import { useEffect, useState } from 'react'
import { getObservationFacts } from '../../api/observationFacts'
import { localizeError, useI18n } from '../../i18n'
import type { Location } from '../../types/location'
import type { ObservationFacts } from '../../types/observationFacts'
import { ViewingConditionsSummary } from '../outlook/ViewingConditionsSummary'
import { InfoHint } from '../InfoHint'
import './PlaceComparison.css'

type Props = {
  places: Location[]
  limit: boolean
  onSelect: (location: Location) => void
  onRemove: (id: number) => void
}

export function PlaceComparison({ places, limit, onSelect, onRemove }: Props) {
  const { t } = useI18n()
  const [revision, setRevision] = useState(0)
  return <section className="place-comparison" aria-labelledby="place-comparison-title">
    <header className="comparison-heading"><h2 id="place-comparison-title">{t('compareTitle')}<InfoHint>{t('compareNote')}<p>{t('compareSources')} <a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA</a> · <a href="https://api.met.no/weatherapi/locationforecast/2.0/documentation" target="_blank" rel="noreferrer">MET Norway</a> · <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a></p></InfoHint></h2>{places.length > 0 && <button className="comparison-action" type="button" onClick={() => setRevision(value => value + 1)}>{t('compareRefresh')}</button>}</header>
    {limit && <p role="status">{t('compareLimit')}</p>}
    {places.length < 2 && <p className="comparison-empty" role="status">{t(places.length === 0 ? 'compareEmpty' : 'compareAddAnother')}</p>}
    <div className="comparison-grid">{places.map(place => <ComparisonRow key={place.id} place={place} revision={revision} onSelect={onSelect} onRemove={() => onRemove(place.id)} />)}</div>
  </section>
}

function ComparisonRow({ place, revision, onSelect, onRemove }: {
  place: Location; revision: number; onSelect: (location: Location) => void; onRemove: () => void
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
    <header><h3>{place.name}, {place.country}</h3><button className="comparison-remove" type="button" aria-label={`${t('compareRemove')} ${place.name}, ${place.country}`} onClick={onRemove}>{t('compareRemove')}</button></header>
    {loading && <p role="status">{t('loading')}</p>}
    {error !== null && <p role="status">{localizeError(error, t)}</p>}
    {!loading && facts && <ViewingConditionsSummary facts={facts} />}
    <button className="comparison-action comparison-map" type="button" onClick={() => onSelect(place)}>{t('compareMap')}</button>
  </article>
}
