import { KpPeakForecast } from './KpPeakForecast'
import { InfoHint } from '../InfoHint'
import type { AuroraMapData, AuroraMapPoint } from '../../types/auroraMap'
import { useI18n } from '../../i18n'
import { activityCoordinates, activityPointKey } from './activityAreaNames'

type Props = {
  data: AuroraMapData | null
  points: AuroraMapPoint[]
  areaNames: Record<string, string>
  error: string
  loading: boolean
  selectedIndex: number | null
  onSelect: (index: number) => void
}

export function CurrentActivityAreas({ data, points, areaNames, error, loading, selectedIndex, onSelect }: Props) {
  const { t } = useI18n()

  return <section className="current-activity-areas" aria-labelledby="current-activity-title">
    <h2 id="current-activity-title">{t('currentAreasTitle')}<InfoHint><p>{t('currentAreasNote')}</p><p>{t('activityAreasMapHint')}</p></InfoHint></h2>
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
            title={activityCoordinates(point)}
            onClick={() => onSelect(index)}
          >
            <span className="activity-area-number" aria-hidden="true">{index + 1}</span>
            <span className="activity-area-location">
            <span className="activity-area-name">{areaNames[activityPointKey(point)] || t('modelGridPoint')}</span>
            </span>
          <span className={`activity-area-level activity-area-level-${level.slice('activity'.length).toLowerCase()}`}>
            <span className="activity-mini-bar" aria-hidden="true"><i style={{ width: `${point.auroraValue}%` }} /></span>{t(level)} <small>{t('activityValue')} {point.auroraValue}</small>
          </span>
          </button>
        </li>
      })}
    </ol>}
    {!loading && !error && data?.status === 'CURRENT' && points.length === 0 && <p className="activity-areas-message">{t('activityAreasUnavailable')}</p>}
    <KpPeakForecast />
  </section>
}
