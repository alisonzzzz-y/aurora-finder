import { StormActivityAlert } from '../components/aurora/StormActivityAlert'
import { ViewingConditionsSummary } from '../components/outlook/ViewingConditionsSummary'
import { MonitoringSections } from '../components/monitoring/MonitoringSections'
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
import { lazy, Suspense, useMemo, useState } from 'react'
import { useActivityAreaNames } from '../hooks/useActivityAreaNames'
import { strongestDistinctPoints } from '../components/aurora/activityPoints'

const AuroraMap = lazy(() => import('../components/aurora/AuroraMap').then(module => ({ default: module.AuroraMap })))

type Props = {
  busy: boolean
  facts: ObservationFacts | null
  error: string
  onSelect: (location: Location) => void
}

export function LocationSearchPage({ busy, facts, error, onSelect }: Props) {
  const { t, language } = useI18n()
  const auroraMap = useAuroraMapData()
  const [selectedActivityIndex, setSelectedActivityIndex] = useState<number | null>(null)
  const [sidebarView, setSidebarView] = useState<'activity' | 'local'>('activity')
  const [comparisonPlaces, setComparisonPlaces] = useState<Location[]>([])
  const [comparisonLimit, setComparisonLimit] = useState(false)
  const [mapLocation, setMapLocation] = useState<Location | null>(null)
  const activityPoints = useMemo(
    () => auroraMap.data?.status === 'CURRENT' ? strongestDistinctPoints(auroraMap.data.points) : [],
    [auroraMap.data],
  )
  const activityAreaNames = useActivityAreaNames(activityPoints, language)
  const hasLocationRequest = busy || Boolean(facts) || Boolean(error)
  const visibleFacts = !busy && !error && facts && (!mapLocation || facts.outlook.location.id === mapLocation.id) ? facts : null
  const c = language === 'zh' ? {
    details: '查看详细预报 ↓', back: '返回地图 ↑', title: '的极光与观测条件', compare: '添加对比地点',
  } : { details: 'View detailed forecast ↓', back: 'Back to map ↑', title: 'Aurora and viewing conditions in ', compare: 'Add a place to compare' }
  function moveTo(id: string) {
    const target = document.getElementById(id)
    target?.focus({ preventScroll: true })
    target?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' })
  }

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
    moveTo('location-comparison')
  }

  return <>
    <StormActivityAlert />
    <section id="aurora-dashboard" tabIndex={-1} className="home-dashboard-grid" aria-label={t('mapAndSearch')}>
      <div className="map-column">
        <Suspense fallback={<div className="aurora-map-loading-placeholder" role="status">{t('loading')}</div>}>
          <AuroraMap data={auroraMap.data} forecastError={auroraMap.error} forecastLoading={auroraMap.loading} activityPoints={activityPoints} activityAreaNames={activityAreaNames} selectedActivityIndex={selectedActivityIndex} onSelectActivity={setSelectedActivityIndex} selectedLocation={mapLocation} />
        </Suspense>

      </div>
      <aside className="map-sidebar map-sidebar-activity map-sidebar-summary">
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}<InfoHint>{t('mapScopeNote')}</InfoHint></h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={busy} onSelect={selectLocation} />
        <div className="map-sidebar-content">
          {(hasLocationRequest || comparisonPlaces.length > 0) && <div className="map-sidebar-switch" role="group" aria-label={t('mapSidebarView')}>
            <button type="button" aria-pressed={sidebarView === 'activity'} onClick={() => setSidebarView('activity')}>{t('activityAreasTab')}</button>
            <button type="button" aria-pressed={sidebarView === 'local'} onClick={() => setSidebarView('local')}>{t('localOutlook')}</button>
          </div>}
          {sidebarView === 'activity' && <CurrentActivityAreas points={activityPoints} areaNames={activityAreaNames} data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} selectedIndex={selectedActivityIndex} onSelect={setSelectedActivityIndex} />}
          {sidebarView === 'local' && <div className="map-sidebar-location-panel">
            {mapLocation && <h3>{mapLocation.name}, {mapLocation.country}</h3>}
            {busy && <p className="location-result-status" role="status">{t('loading')}</p>}
            {error && <p className="error location-result-status" role="alert">{localizeError(new Error(error), t)}</p>}
            {visibleFacts && <>
              <ViewingConditionsSummary facts={visibleFacts} compact />
              <div className="location-summary-actions">
                <button className="comparison-action" type="button" onClick={() => moveTo('location-details')}>{c.details}</button>
                <button className="comparison-action" type="button" disabled={comparisonPlaces.some(place => place.id === visibleFacts.outlook.location.id)} onClick={() => addComparisonPlace(visibleFacts.outlook.location)}>{t(comparisonPlaces.some(place => place.id === visibleFacts.outlook.location.id) ? 'compareAdded' : 'compareButton')}</button>
              </div>
              {comparisonLimit && <p role="status">{t('compareLimit')}</p>}
            </>}
          </div>}

        </div>
      </aside>
    </section>
    <div className="home-content-sections">
      {(hasLocationRequest || comparisonPlaces.length > 0) && <section id="location-details" tabIndex={-1} className="home-section-band home-section-band-even location-details-band" aria-labelledby="location-details-title">
        <header className="location-details-heading"><h2 id="location-details-title">{mapLocation ? language === 'zh' ? `${mapLocation.name}${c.title}` : `${c.title}${mapLocation.name}` : t('localOutlook')}</h2><button className="comparison-action" type="button" onClick={() => moveTo('aurora-dashboard')}>{c.back}</button></header>
        {busy && <p role="status">{t('loading')}</p>}
        {error && <p role="alert" className="error">{localizeError(new Error(error), t)}</p>}
        {visibleFacts && <LocalOutlookDashboard key={visibleFacts.outlook.location.id} facts={visibleFacts} />}
        <div id="location-comparison" tabIndex={-1} className="location-details-comparison">
          <LocationSearch busy={false} title={c.compare} buttonLabel={t('compareButton')} onSelect={addComparisonPlace} />
          <PlaceComparison places={comparisonPlaces} limit={comparisonLimit} onSelect={location => {
            selectLocation(location)
            moveTo('aurora-dashboard')
          }} onRemove={id => {
            setComparisonPlaces(previous => previous.filter(place => place.id !== id))
            setComparisonLimit(false)
          }} />
        </div>
      </section>}
      <div className="home-section-band home-section-band-odd" role="region" aria-label={t('globalKpActivity')}>
        <LatestAuroraForecast />
      </div>
      <div className="home-section-band home-section-band-even" role="region" aria-label={t('stormOutlookTitle')}>
        <NextAuroraStormForecast />
      </div>
      <MonitoringSections facts={facts} />
      <div className="home-section-band home-section-band-odd" role="region" aria-label={t('historicalReportsTitle')}>
        <HistoricalReportsChart />
      </div>
      <div className="home-section-band home-section-band-even" role="region" aria-label={t('forecastGuideTitle')}>
        <ForecastGuide />
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
