import { InfoHint } from '../components/InfoHint'
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
import { lazy, Suspense, useLayoutEffect, useMemo, useRef, useState } from 'react'
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
  const sidebarContentRef = useRef<HTMLDivElement>(null)
  const [selectedActivityIndex, setSelectedActivityIndex] = useState<number | null>(null)
  const [sidebarView, setSidebarView] = useState<'activity' | 'local' | 'comparison'>('activity')
  const [comparisonPlaces, setComparisonPlaces] = useState<Location[]>([])
  const [comparisonLimit, setComparisonLimit] = useState(false)
  const [mapLocation, setMapLocation] = useState<Location | null>(null)
  const activityPoints = useMemo(
    () => auroraMap.data?.status === 'CURRENT' ? strongestDistinctPoints(auroraMap.data.points) : [],
    [auroraMap.data],
  )
  useLayoutEffect(() => {
    if (sidebarContentRef.current) sidebarContentRef.current.scrollTop = 0
  }, [sidebarView, mapLocation])
  const hasLocationRequest = busy || Boolean(facts) || Boolean(error)

  function selectLocation(location: Location) {
    setSelectedActivityIndex(null)
    // A new object also lets a repeated click recenter after manually panning.
    setMapLocation({ ...location })
    setSidebarView('local')
    onSelect(location)
  }

  function addComparisonPlace(location: Location) {
    if (!comparisonPlaces.some(place => place.id === location.id)) {
      if (comparisonPlaces.length >= 3) {
        setComparisonLimit(true)
        return
      }
      setComparisonPlaces(previous => [...previous, location])
    }
    setComparisonLimit(false)
    setSidebarView('comparison')
  }

  return <>
    <section id="aurora-dashboard" className="home-dashboard-grid" aria-label={t('mapAndSearch')}>
      <div className="map-column">
        <Suspense fallback={<div className="aurora-map-loading-placeholder" role="status">{t('loading')}</div>}>
          <AuroraMap data={auroraMap.data} forecastError={auroraMap.error} forecastLoading={auroraMap.loading} activityPoints={activityPoints} selectedActivityIndex={selectedActivityIndex} onSelectActivity={setSelectedActivityIndex} selectedLocation={mapLocation} />
        </Suspense>

      </div>
      <aside className={`map-sidebar${sidebarView === 'comparison' || (hasLocationRequest && sidebarView === 'local') ? "" : " map-sidebar-activity"}`}>
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}<InfoHint>{t('mapScopeNote')}</InfoHint></h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={sidebarView === 'comparison' ? false : busy} buttonLabel={sidebarView === 'comparison' ? t('compareButton') : undefined} onSelect={sidebarView === 'comparison' ? addComparisonPlace : selectLocation} />
        <div className="map-sidebar-content" ref={sidebarContentRef}>
          {(hasLocationRequest || comparisonPlaces.length > 0) && <div className="map-sidebar-switch" role="group" aria-label={t('mapSidebarView')}>
            <button type="button" aria-pressed={sidebarView === 'activity'} onClick={() => setSidebarView('activity')}>{t('activityAreasTab')}</button>
            <button type="button" aria-pressed={sidebarView === 'local'} onClick={() => setSidebarView('local')}>{t('localOutlook')}</button>
            <button type="button" aria-pressed={sidebarView === 'comparison'} onClick={() => setSidebarView('comparison')}>{t('compareTitle')}{comparisonPlaces.length > 0 && ` (${comparisonPlaces.length})`}</button>
          </div>}
          {sidebarView === 'activity' && <CurrentActivityAreas data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} selectedIndex={selectedActivityIndex} onSelect={setSelectedActivityIndex} />}
          {sidebarView === 'local' && <div className="map-sidebar-location-panel">
            {mapLocation && <button className="comparison-action comparison-add-current" type="button" disabled={comparisonPlaces.some(place => place.id === mapLocation.id) || comparisonPlaces.length >= 3} onClick={() => addComparisonPlace(mapLocation)}>{t(comparisonPlaces.some(place => place.id === mapLocation.id) ? 'compareAdded' : 'compareButton')}</button>}
            {busy && <p className="location-result-status" role="status">{t('loading')}</p>}
            {error && <p className="error location-result-status" role="alert">{localizeError(new Error(error), t)}</p>}
            {facts && <LocalOutlookDashboard key={facts.outlook.location.id} facts={facts} />}
          </div>}
          {sidebarView === 'comparison' && <PlaceComparison places={comparisonPlaces} limit={comparisonLimit} onSelect={location => {
            selectLocation(location)
            document.getElementById('aurora-dashboard')?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' })
          }} onRemove={id => {
            setComparisonPlaces(previous => previous.filter(place => place.id !== id))
            setComparisonLimit(false)
          }} />}

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

    <h2 id="forecast-guide-title">{t('forecastGuideTitle')}<InfoHint>{t('forecastGuideIntro')}</InfoHint></h2>
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
