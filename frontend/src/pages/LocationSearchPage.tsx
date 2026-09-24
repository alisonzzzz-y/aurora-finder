import { LocationSearch } from '../components/location/LocationSearch'
import { AuroraMap } from '../components/aurora/AuroraMap'
import { LatestAuroraForecast } from '../components/aurora/LatestAuroraForecast'
import { CurrentActivityAreas } from '../components/aurora/CurrentActivityAreas'
import type { Location } from '../types/location'
import { useI18n } from '../i18n'
import { useAuroraMapData } from '../hooks/useAuroraMapData'

type Props = { busy: boolean; onSelect: (location: Location) => void }

export function LocationSearchPage({ busy, onSelect }: Props) {
  const { t } = useI18n()
  const auroraMap = useAuroraMapData()
  return <>
    <section className="home-dashboard-grid" aria-label={t('mapAndSearch')}>
      <div className="map-column">
        <AuroraMap data={auroraMap.data} forecastError={auroraMap.error} forecastLoading={auroraMap.loading} />
        <LatestAuroraForecast />
      </div>
      <aside className="map-sidebar">
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}</h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={busy} onSelect={onSelect} />
        <CurrentActivityAreas data={auroraMap.data} error={auroraMap.error} loading={auroraMap.loading} />
      </aside>
    </section>
    <section className="intro home-intro">
      <p className="eyebrow">{t('planNight')}</p>
      <h1>{t('heroTitle')}</h1>
      <p className="intro-copy">{t('heroCopy')}</p>
    </section>
  </>
}
