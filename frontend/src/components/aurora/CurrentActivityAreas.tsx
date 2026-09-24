import type { AuroraMapData } from '../../types/auroraMap'
import { useI18n } from '../../i18n'
import { strongestDistinctPoints } from './activityPoints'

type Props = {
  data: AuroraMapData | null
  error: string
  loading: boolean
  selectedIndex: number | null
  onSelect: (index: number) => void
}

function formatCoordinate(value: number, positive: string, negative: string) {
  const direction = value < 0 ? negative : positive
  return `${Math.abs(value).toFixed(1)}° ${direction}`
}

export function CurrentActivityAreas({ data, error, loading, selectedIndex, onSelect }: Props) {
  const { t } = useI18n()
  const points = data?.status === 'CURRENT' ? strongestDistinctPoints(data.points) : []

  return <section className="current-activity-areas" aria-labelledby="current-activity-title">
    <p className="eyebrow">{t('currentAreasLabel')}</p>
    <h2 id="current-activity-title">{t('currentAreasTitle')}</h2>
    <p className="activity-areas-hint">{t('activityAreasMapHint')}</p>
    {loading && <p className="activity-areas-message" role="status">{t('activityAreasLoading')}</p>}
    {!loading && error && <p className="activity-areas-message" role="status">{t('activityAreasUnavailable')}</p>}
    {!loading && !error && data?.status === 'EXPIRED' && <p className="activity-areas-message" role="status">{t('activityAreasExpired')}</p>}
    {!loading && !error && data?.status === 'CURRENT' && points.length > 0 && <ol className="activity-area-list">
      {points.map((point, index) => {
        const level = point.auroraValue < 18 ? 'activityLow' : point.auroraValue < 50 ? 'activityMedium' : 'activityHigh'
        return <li key={`${point.latitude}:${point.longitude}`}>
          <button
            className={`activity-area-select${selectedIndex === index ? ' is-selected' : ''}`}
            type="button"
            aria-pressed={selectedIndex === index}
            onClick={() => onSelect(index)}
          >
            <span className="activity-area-number" aria-hidden="true">{index + 1}</span>
            <span className="activity-area-location">
            <span className="activity-area-coordinates">{formatCoordinate(point.latitude, 'N', 'S')} · {formatCoordinate(point.longitude, 'E', 'W')}</span>
            <span className="activity-area-caption">{t('modelGridPoint')}</span>
            </span>
          <span className={`activity-area-level activity-area-level-${level.slice('activity'.length).toLowerCase()}`}>
            {t(level)} <small>{t('activityValue')} {point.auroraValue}</small>
          </span>
          </button>
        </li>
      })}
    </ol>}
    {!loading && !error && data?.status === 'CURRENT' && points.length === 0 && <p className="activity-areas-message">{t('activityAreasUnavailable')}</p>}
    <p className="activity-areas-note">{t('currentAreasNote')}</p>
  </section>
}
