import type { Outlook } from '../types/outlook'
import { NightOutlookCard } from '../components/outlook/NightOutlookCard'
import '../components/outlook/OutlookPage.css'

type Props = { outlook: Outlook; onChangeLocation: () => void }

const ruleStatusLabels: Record<Outlook['ruleStatus'], string> = {
  NOT_VALIDATED: 'Viewing rules not validated',
  VALIDATED: 'Viewing rules validated',
}

function formatLocalTimestamp(instant: string, timezone: string) {
  const date = new Date(instant)
  const localTime = new Intl.DateTimeFormat('en', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(date)
  const offset = new Intl.DateTimeFormat('en', {
    hour: '2-digit', timeZone: timezone, timeZoneName: 'longOffset',
  }).formatToParts(date).find(part => part.type === 'timeZoneName')?.value ?? 'GMT'
  return `${localTime} (${offset.replace(/^GMT/, 'UTC')})`
}

export function OutlookPage({ outlook, onChangeLocation }: Props) {
  return <>
    <section className="outlook" aria-labelledby="outlook-title">
      <div className="section-heading">
        <div><p className="eyebrow">LOCAL OUTLOOK</p><h1 id="outlook-title">{outlook.location.name}, {outlook.location.country}</h1></div>
        <div className="place-actions"><span>{outlook.location.timezone}</span><button type="button" className="text-button" onClick={onChangeLocation}>Choose another place</button></div>
      </div>
      <p className="rule-status">{ruleStatusLabels[outlook.ruleStatus]}</p>
      <div className="night-grid">{outlook.nights.map((night, index) => <NightOutlookCard night={night} index={index} key={night.localDate} />)}</div>
      <p className="timestamp">Response generated {formatLocalTimestamp(outlook.generatedAtUtc, outlook.location.timezone)}. Dates and times follow the selected place's local time.</p>
    </section>
    <section className="foundation-grid" aria-label="Upcoming features and sources">
      <article className="feature-card"><p className="eyebrow">AURORA MAP</p><h2>Short-range activity</h2><p>The NOAA OVATION layer will appear here only when its forecast time and data freshness have been checked. The model's aurora area is not a ground visibility boundary.</p><span className="coming-soon">Awaiting integration</span></article>
      <article className="feature-card"><p className="eyebrow">SOURCE NOTES</p><h2>Know what supports the outlook</h2><p>NOAA aurora and Kp forecasts, MET Norway cloud forecasts, and local darkness need separate timestamps and coverage checks. Missing data must stay visible.</p><div className="source-links"><a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA OVATION ↗</a><a href="https://docs.api.met.no/doc/locationforecast/datamodel.html" target="_blank" rel="noreferrer">MET Norway ↗</a><a href="https://open-meteo.com/en/docs/geocoding-api" target="_blank" rel="noreferrer">Open-Meteo geocoding ↗</a></div></article>
    </section>
  </>
}
