import type { AuroraMapData, AuroraMapPoint } from '../../types/auroraMap'
import { useI18n } from '../../i18n'

type Props = { data: AuroraMapData | null; error: string; loading: boolean }

function distanceKm(a: AuroraMapPoint, b: AuroraMapPoint) {
  const radians = Math.PI / 180
  const latA = a.latitude * radians
  const latB = b.latitude * radians
  const latDelta = (b.latitude - a.latitude) * radians
  let longitudeDelta = (b.longitude - a.longitude) * radians
  if (longitudeDelta > Math.PI) longitudeDelta -= Math.PI * 2
  if (longitudeDelta < -Math.PI) longitudeDelta += Math.PI * 2
  const haversine = Math.sin(latDelta / 2) ** 2
    + Math.cos(latA) * Math.cos(latB) * Math.sin(longitudeDelta / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

function strongestDistinctPoints(points: AuroraMapPoint[]) {
  const ranked = [...points].sort((a, b) => b.auroraValue - a.auroraValue)
  const selected: AuroraMapPoint[] = []
  for (const point of ranked) {
    if (selected.every(previous => distanceKm(previous, point) >= 700)) selected.push(point)
    if (selected.length === 3) break
  }
  return selected
}

function formatCoordinate(value: number, positive: string, negative: string) {
  const direction = value < 0 ? negative : positive
  return `${Math.abs(value).toFixed(1)}° ${direction}`
}

export function CurrentActivityAreas({ data, error, loading }: Props) {
  const { t } = useI18n()
  const points = data?.status === 'CURRENT' ? strongestDistinctPoints(data.points) : []

  return <section className="current-activity-areas" aria-labelledby="current-activity-title">
    <p className="eyebrow">{t('currentAreasLabel')}</p>
    <h2 id="current-activity-title">{t('currentAreasTitle')}</h2>
    {loading && <p className="activity-areas-message" role="status">{t('activityAreasLoading')}</p>}
    {!loading && error && <p className="activity-areas-message" role="status">{t('activityAreasUnavailable')}</p>}
    {!loading && !error && data?.status === 'EXPIRED' && <p className="activity-areas-message" role="status">{t('activityAreasExpired')}</p>}
    {!loading && !error && data?.status === 'CURRENT' && points.length > 0 && <ol className="activity-area-list">
      {points.map(point => {
        const level = point.auroraValue < 18 ? 'activityLow' : point.auroraValue < 50 ? 'activityMedium' : 'activityHigh'
        return <li key={`${point.latitude}:${point.longitude}`}>
          <div>
            <span className="activity-area-coordinates">{formatCoordinate(point.latitude, 'N', 'S')} · {formatCoordinate(point.longitude, 'E', 'W')}</span>
            <span className="activity-area-caption">{t('modelGridPoint')}</span>
          </div>
          <span className={`activity-area-level activity-area-level-${level.slice('activity'.length).toLowerCase()}`}>
            {t(level)} <small>{t('activityValue')} {point.auroraValue}</small>
          </span>
        </li>
      })}
    </ol>}
    {!loading && !error && data?.status === 'CURRENT' && points.length === 0 && <p className="activity-areas-message">{t('activityAreasUnavailable')}</p>}
    <p className="activity-areas-note">{t('currentAreasNote')}</p>
  </section>
}
