import { LocationSearch } from '../components/location/LocationSearch'
import { AuroraMap } from '../components/aurora/AuroraMap'
import { LatestAuroraForecast } from '../components/aurora/LatestAuroraForecast'
import { CurrentActivityAreas } from '../components/aurora/CurrentActivityAreas'
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
        <LatestAuroraForecast />
      </div>
      <aside className="map-sidebar">
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}</h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={busy} onSelect={onSelect} />
        <CurrentActivityAreas data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} selectedIndex={selectedActivityIndex} onSelect={setSelectedActivityIndex} />
      </aside>
    </section>
    <AuroraResources />
  </>
}
