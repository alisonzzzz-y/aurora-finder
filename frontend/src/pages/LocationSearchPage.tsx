import { LocationSearch } from '../components/location/LocationSearch'
import { AuroraMap } from '../components/aurora/AuroraMap'
import { LatestAuroraForecast } from '../components/aurora/LatestAuroraForecast'
import { CurrentActivityAreas } from '../components/aurora/CurrentActivityAreas'
import { NextAuroraStormForecast } from '../components/aurora/NextAuroraStormForecast'
import { AuroraResources } from '../components/aurora/AuroraResources'
import type { Location } from '../types/location'
import { useI18n } from '../i18n'
import { useAuroraMapData } from '../hooks/useAuroraMapData'
import { useMemo, useState } from 'react'
import { strongestDistinctPoints } from '../components/aurora/activityPoints'

type Props = { busy: boolean; onSelect: (location: Location) => void }

export function LocationSearchPage({ busy, onSelect }: Props) {
  const { t } = useI18n()
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
      </aside>
    </section>
    <div className="home-content-sections">
      <CurrentActivityAreas data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} selectedIndex={selectedActivityIndex} onSelect={setSelectedActivityIndex} />
      <NextAuroraStormForecast />
      <LatestAuroraForecast />
      <ForecastGuide />
      <UpcomingFeatures />
    </div>
    <AuroraResources />
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
        <div><h3>{t(title)}</h3><p>{t(copy)}</p></div>
      </li>)}
    </ol>
  </section>
}

function UpcomingFeatures() {
  const { t } = useI18n()
  const items = [
    ['nextStormTitle', 'nextStormCopy'],
    ['nextSpotsTitle', 'nextSpotsCopy'],
    ['nextAiTitle', 'nextAiCopy'],
    ['nextCompareTitle', 'nextCompareCopy'],
  ] as const

  return <section className="upcoming-features" aria-labelledby="upcoming-features-title">
    <p className="eyebrow">{t('upcomingFeaturesEyebrow')}</p>
    <h2 id="upcoming-features-title">{t('upcomingFeaturesTitle')}</h2>
    <p className="upcoming-features-intro">{t('upcomingFeaturesIntro')}</p>
    <ol className="upcoming-feature-list">
      {items.map(([title, copy], index) => <li key={title}>
        <span className="upcoming-feature-number" aria-hidden="true">0{index + 1}</span>
        <div><h3>{t(title)}</h3><p>{t(copy)}</p><span className="upcoming-feature-state">{t('inDevelopment')}</span></div>
      </li>)}
    </ol>
  </section>
}
