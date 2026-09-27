import { LocationSearch } from '../components/location/LocationSearch'
import { AuroraMap } from '../components/aurora/AuroraMap'
import { LocalAuroraActivityCard } from '../components/aurora/LocalAuroraActivityCard'
import { NightOutlookCard } from '../components/outlook/NightOutlookCard'
import { CloudForecastCard } from '../components/weather/CloudForecastCard'
import { LatestAuroraForecast } from '../components/aurora/LatestAuroraForecast'
import { CurrentActivityAreas } from '../components/aurora/CurrentActivityAreas'
import { HistoricalReportsChart } from '../components/aurora/HistoricalReportsChart'
import { NextAuroraStormForecast } from '../components/aurora/NextAuroraStormForecast'
import { AuroraResources } from '../components/aurora/AuroraResources'
import type { Location } from '../types/location'
import type { ObservationFacts } from '../types/observationFacts'
import { localizeError, useI18n } from '../i18n'
import { useAuroraMapData } from '../hooks/useAuroraMapData'
import { useMemo, useState } from 'react'
import { strongestDistinctPoints } from '../components/aurora/activityPoints'

type Props = {
  busy: boolean
  facts: ObservationFacts | null
  error: string
  onSelect: (location: Location) => void
}

export function LocationSearchPage({ busy, facts, error, onSelect }: Props) {
  const { language, t } = useI18n()
  const auroraMap = useAuroraMapData()
  const [selectedActivityIndex, setSelectedActivityIndex] = useState<number | null>(null)
  const activityPoints = useMemo(
    () => auroraMap.data?.status === 'CURRENT' ? strongestDistinctPoints(auroraMap.data.points) : [],
    [auroraMap.data],
  )
  return <>
    <section className="home-dashboard-grid" aria-label={t('mapAndSearch')}>
      <div className="map-column">
        <AuroraMap data={auroraMap.data} forecastError={auroraMap.error} forecastLoading={auroraMap.loading} activityPoints={activityPoints} selectedActivityIndex={selectedActivityIndex} onSelectActivity={setSelectedActivityIndex} />
        <p className="map-scope-note">{t('mapScopeNote')}</p>
      </div>
      <aside className="map-sidebar">
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}</h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={busy} onSelect={onSelect} />
        {busy && <p className="location-result-status" role="status">{t('loading')}</p>}
        {error && <p className="error location-result-status" role="alert">{localizeError(new Error(error), t)}</p>}
        <div className="map-sidebar-content">
          <CurrentActivityAreas data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} selectedIndex={selectedActivityIndex} onSelect={setSelectedActivityIndex} />
          {facts && <SelectedLocationOutlook facts={facts} language={language} />}
        </div>
      </aside>
    </section>
    <div className="home-content-sections">
      <div className="home-section-band home-section-band-odd" role="region" aria-label={t('globalKpActivity')}>
        <LatestAuroraForecast />
      </div>
      <div className="home-section-band home-section-band-even" role="region" aria-label={t('stormOutlookTitle')}>
        <NextAuroraStormForecast />
      </div>
      <div className="home-section-band home-section-band-odd" role="region" aria-label={t('historicalReportsTitle')}>
        <HistoricalReportsChart />
      </div>
      <div className="home-section-band home-section-band-even" role="region" aria-label={t('forecastGuideTitle')}>
        <ForecastGuide />
      </div>
      <div className="home-section-band home-section-band-odd" role="region" aria-label={t('upcomingFeaturesTitle')}>
        <UpcomingFeatures />
      </div>
    </div>
    <div className="home-section-band home-section-band-even resources-band" role="region" aria-label={t('sourceNotesTitle')}>
      <AuroraResources />
    </div>
  </>
}

function SelectedLocationOutlook({ facts, language }: { facts: ObservationFacts; language: 'en' | 'zh' }) {
  const { t } = useI18n()
  const { outlook } = facts
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const generatedAt = new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: outlook.location.timezone,
  }).format(new Date(facts.generatedAtUtc))

  return <section className="selected-location-outlook" aria-labelledby="selected-location-title" aria-live="polite">
    <div className="selected-location-heading">
      <p className="eyebrow">{t('localOutlook')}</p>
      <h2 id="selected-location-title">{outlook.location.name}, {outlook.location.country}</h2>
      <p>{outlook.location.timezone}</p>
    </div>
    <p className={`source-status source-status-${facts.sourceStatus.toLowerCase()}`}>
      {t(facts.sourceStatus === 'CURRENT' ? 'allSourcesAvailable' : facts.sourceStatus === 'PARTIAL' ? 'someSourcesMissing' : 'noSourcesAvailable')}
    </p>
    <p className="rule-status">{t(outlook.ruleStatus === 'VALIDATED' ? 'rulesValidated' : 'rulesNotValidated')}</p>
    <div className="selected-night-list">
      {outlook.nights.map((night, index) => <NightOutlookCard night={night} index={index} timezone={outlook.location.timezone} key={night.localDate} />)}
    </div>
    <p className="timestamp">{t('generatedAt')} {generatedAt}. {t('localTimeNote')}</p>
    <LocalAuroraActivityCard fact={facts.auroraActivity} timezone={outlook.location.timezone} />
    <CloudForecastCard fact={facts.cloudForecast} timezone={outlook.location.timezone} night={outlook.nights[0]} />
  </section>
}

function ForecastGuide() {
  const { t } = useI18n()
  const items = [
    ['guideMapTitle', 'guideMapCopy'],
    ['guideKpTitle', 'guideKpCopy'],
    ['guideLocalTitle', 'guideLocalCopy'],
  ] as const

  return <section className="forecast-guide" aria-labelledby="forecast-guide-title">
    <p className="eyebrow">{t('forecastGuideEyebrow')}</p>
    <h2 id="forecast-guide-title">{t('forecastGuideTitle')}</h2>
    <p className="forecast-guide-intro">{t('forecastGuideIntro')}</p>
    <ol className="forecast-guide-list">
      {items.map(([title, copy], index) => <li key={title}>
        <span className="forecast-guide-number" aria-hidden="true">0{index + 1}</span>
        <div><h3>{t(title)}</h3><p>{t(copy)}</p></div>
      </li>)}
    </ol>
  </section>
}

function UpcomingFeatures() {
  const { t } = useI18n()
  const items = [
    ['nextStormTitle', 'nextStormCopy', 'inDevelopment'],
    ['nextSpotsTitle', 'nextSpotsCopy', 'inDevelopment'],
    ['nextAiTitle', 'nextAiCopy', 'availableNow'],
    ['nextCompareTitle', 'nextCompareCopy', 'inDevelopment'],
  ] as const

  return <section className="upcoming-features" aria-labelledby="upcoming-features-title">
    <p className="eyebrow">{t('upcomingFeaturesEyebrow')}</p>
    <h2 id="upcoming-features-title">{t('upcomingFeaturesTitle')}</h2>
    <p className="upcoming-features-intro">{t('upcomingFeaturesIntro')}</p>
    <ol className="upcoming-feature-list">
      {items.map(([title, copy, state], index) => <li key={title}>
        <span className="upcoming-feature-number" aria-hidden="true">0{index + 1}</span>
        <div><h3>{t(title)}</h3><p>{t(copy)}</p><span className="upcoming-feature-state">{t(state)}</span></div>
      </li>)}
    </ol>
  </section>
}
