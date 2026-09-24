import { LocationSearch } from '../components/location/LocationSearch'
import { AuroraMap } from '../components/aurora/AuroraMap'
import { LatestAuroraForecast } from '../components/aurora/LatestAuroraForecast'
import type { Location } from '../types/location'
import { useI18n } from '../i18n'

type Props = { busy: boolean; onSelect: (location: Location) => void }

export function LocationSearchPage({ busy, onSelect }: Props) {
  const { t } = useI18n()
  return <>
    <section className="home-dashboard-grid" aria-label={t('mapAndSearch')}>
      <div className="map-column">
        <AuroraMap />
        <LatestAuroraForecast />
      </div>
      <aside className="map-sidebar">
        <div className="map-context-heading" aria-label={t('auroraForecast')}>
          <p className="eyebrow">{t('globalActivity')}</p>
          <h2>{t('auroraForecast')}</h2>
          <span>{t('shortRange')}</span>
        </div>
        <LocationSearch busy={busy} onSelect={onSelect} />
        <article className="city-ranking-note">
          <p className="eyebrow">{t('cityOutlooks')}</p>
          <h2>{t('localConditionsTitle')}</h2>
          <p>{t('cityOutlooksNote')}</p>
        </article>
      </aside>
    </section>
    <section className="intro home-intro">
      <p className="eyebrow">{t('planNight')}</p>
      <h1>{t('heroTitle')}</h1>
      <p className="intro-copy">{t('heroCopy')}</p>
    </section>
  </>
}
