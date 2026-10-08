import { PlaceComparison } from '../components/location/PlaceComparison'
import { LocationSearch } from '../components/location/LocationSearch'
import { LocalOutlookDashboard } from '../components/outlook/LocalOutlookDashboard'
import { LatestAuroraForecast } from '../components/aurora/LatestAuroraForecast'
import { CurrentActivityAreas } from '../components/aurora/CurrentActivityAreas'
import { HistoricalReportsChart } from '../components/aurora/HistoricalReportsChart'
import { NextAuroraStormForecast } from '../components/aurora/NextAuroraStormForecast'
import { AuroraResources } from '../components/aurora/AuroraResources'
import type { Location } from '../types/location'
import type { ObservationFacts } from '../types/observationFacts'
import { localizeError, useI18n } from '../i18n'
import { useAuroraMapData } from '../hooks/useAuroraMapData'
import { lazy, Suspense, useMemo, useState } from 'react'
import { strongestDistinctPoints } from '../components/aurora/activityPoints'

const AuroraMap = lazy(() => import('../components/aurora/AuroraMap').then(module => ({ default: module.AuroraMap })))

type Props = {
  busy: boolean
  facts: ObservationFacts | null
  error: string
  onSelect: (location: Location) => void
}

export function LocationSearchPage({ busy, facts, error, onSelect }: Props) {
  const { t } = useI18n()
  const auroraMap = useAuroraMapData()
  const [selectedActivityIndex, setSelectedActivityIndex] = useState<number | null>(null)
  const [showLocalOutlook, setShowLocalOutlook] = useState(false)
  const activityPoints = useMemo(
    () => auroraMap.data?.status === 'CURRENT' ? strongestDistinctPoints(auroraMap.data.points) : [],
    [auroraMap.data],
  )
  const hasLocationRequest = busy || Boolean(facts) || Boolean(error)

  function selectLocation(location: Location) {
    setShowLocalOutlook(true)
    onSelect(location)
  }

  return <>
    <section id="aurora-dashboard" className="home-dashboard-grid" aria-label={t('mapAndSearch')}>
      <div className="map-column">
        <Suspense fallback={<div className="aurora-map-loading-placeholder" role="status">{t('loading')}</div>}>
          <AuroraMap data={auroraMap.data} forecastError={auroraMap.error} forecastLoading={auroraMap.loading} activityPoints={activityPoints} selectedActivityIndex={selectedActivityIndex} onSelectActivity={setSelectedActivityIndex} selectedLocation={facts?.outlook.location} />
        </Suspense>
        <p className="map-scope-note">{t('mapScopeNote')}</p>
      </div>
      <aside className="map-sidebar">
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}</h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={busy} onSelect={selectLocation} />
        <div className="map-sidebar-content">
          {hasLocationRequest && <div className="map-sidebar-switch" role="group" aria-label={t('mapSidebarView')}>
            <button type="button" aria-pressed={!showLocalOutlook} onClick={() => setShowLocalOutlook(false)}>{t('activityAreasTab')}</button>
            <button type="button" aria-pressed={showLocalOutlook} onClick={() => setShowLocalOutlook(true)}>{t('localOutlook')}</button>
          </div>}
          {(!hasLocationRequest || !showLocalOutlook) && <CurrentActivityAreas data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} selectedIndex={selectedActivityIndex} onSelect={setSelectedActivityIndex} />}
          {hasLocationRequest && showLocalOutlook && <div className="map-sidebar-location-panel">
            {busy && <p className="location-result-status" role="status">{t('loading')}</p>}
            {error && <p className="error location-result-status" role="alert">{localizeError(new Error(error), t)}</p>}
            {facts && <LocalOutlookDashboard key={facts.outlook.location.id} facts={facts} />}
          </div>}
        </div>
      </aside>
    </section>
    <div className="home-content-sections">
      <div className="home-section-band home-section-band-even" role="region" aria-label={t('compareTitle')}>
        <PlaceComparison onSelect={location => {
          selectLocation(location)
          document.getElementById('aurora-dashboard')?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' })
        }} />
      </div>
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
        <details className="visual-details"><summary>{t('upcomingFeaturesTitle')}</summary><UpcomingFeatures /></details>
      </div>
    </div>
    <div className="home-section-band home-section-band-even resources-band" role="region" aria-label={t('sourceNotesTitle')}>
      <AuroraResources />
    </div>
  </>
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
        <div><h3>{t(title)}</h3><details className="visual-details"><summary>{t('guideDetails')}</summary><p>{t(copy)}</p></details></div>
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
