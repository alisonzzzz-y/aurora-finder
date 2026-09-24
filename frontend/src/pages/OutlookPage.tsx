import { NightOutlookCard } from '../components/outlook/NightOutlookCard'
import { LocalAuroraActivityCard } from '../components/aurora/LocalAuroraActivityCard'
import { CloudForecastCard } from '../components/weather/CloudForecastCard'
import { useI18n } from '../i18n'
import type { ObservationFacts } from '../types/observationFacts'
import '../components/outlook/OutlookPage.css'

type Props = { facts: ObservationFacts; onChangeLocation: () => void }

function formatLocalTimestamp(instant: string, timezone: string, locale: string) {
  const date = new Date(instant)
  const localTime = new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(date)
  const offset = new Intl.DateTimeFormat('en', {
    hour: '2-digit', timeZone: timezone, timeZoneName: 'longOffset',
  }).formatToParts(date).find(part => part.type === 'timeZoneName')?.value ?? 'GMT'
  return `${localTime} (${offset.replace(/^GMT/, 'UTC')})`
}

export function OutlookPage({ facts, onChangeLocation }: Props) {
  const { language, t } = useI18n()
  const { outlook } = facts
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  return <>
    <section className="outlook" aria-labelledby="outlook-title">
      <div className="section-heading">
        <div><p className="eyebrow">{t('localOutlook')}</p><h1 id="outlook-title">{outlook.location.name}, {outlook.location.country}</h1></div>
        <div className="place-actions"><span>{outlook.location.timezone}</span><button type="button" className="text-button" onClick={onChangeLocation}>{t('chooseAnotherPlace')}</button></div>
      </div>
      <p className="rule-status">{t(outlook.ruleStatus === 'VALIDATED' ? 'rulesValidated' : 'rulesNotValidated')}</p>
      <p className={`source-status source-status-${facts.sourceStatus.toLowerCase()}`}>{t(facts.sourceStatus === 'CURRENT' ? 'allSourcesAvailable' : facts.sourceStatus === 'PARTIAL' ? 'someSourcesMissing' : 'noSourcesAvailable')}</p>
      <p className="source-coverage">{facts.coverage.status === 'OVERLAPS'
        ? `${t('forecastCoverageOverlap')} ${facts.coverage.cloudPointsWithValuesInsideShortRange} ${t('cloudPointsOverlapSuffix')}`
        : t(facts.coverage.status === 'NO_OVERLAP' ? 'forecastCoverageNoOverlap' : 'forecastCoverageUnknown')}</p>
      <div className="night-grid">{outlook.nights.map((night, index) => <NightOutlookCard night={night} index={index} timezone={outlook.location.timezone} key={night.localDate} />)}</div>
      <p className="timestamp">{t('generatedAt')} {formatLocalTimestamp(facts.generatedAtUtc, outlook.location.timezone, locale)}. {t('localTimeNote')}</p>
      <LocalAuroraActivityCard fact={facts.auroraActivity} timezone={outlook.location.timezone} />
      <CloudForecastCard fact={facts.cloudForecast} timezone={outlook.location.timezone} />
    </section>
    <section className="foundation-grid" aria-label={t('upcomingFeatures')}>
      <article className="feature-card"><p className="eyebrow">{t('mapFeature')}</p><h2>{t('shortRangeActivity')}</h2><p>{t('mapFeatureNote')}</p><span className="coming-soon">{t('awaitingIntegration')}</span></article>
      <article className="feature-card"><p className="eyebrow">{t('sourceNotes')}</p><h2>{t('sourceNotesTitle')}</h2><p>{t('sourceNotesCopy')}</p><div className="source-links"><a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA OVATION ↗</a><a href="https://docs.api.met.no/doc/locationforecast/datamodel.html" target="_blank" rel="noreferrer">MET Norway ↗</a><a href="https://open-meteo.com/en/docs/geocoding-api" target="_blank" rel="noreferrer">Open-Meteo geocoding ↗</a></div></article>
    </section>
  </>
}
