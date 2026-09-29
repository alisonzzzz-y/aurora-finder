import type { WeatherForecast } from '../../types/weatherForecast'
import type { SourceFact } from '../../types/observationFacts'
import type { LocalAuroraActivity } from '../../types/localAuroraActivity'
import type { NightOutlook } from '../../types/outlook'
import { sortByValidAt } from '../../utils/sortByValidAt'
import { useI18n } from '../../i18n'
import { CloudDarknessTimeline } from './CloudDarknessTimeline'
import './CloudForecastCard.css'

type Props = { fact: SourceFact<WeatherForecast>; timezone: string; night?: NightOutlook; highlightedPointTimes?: string[]; auroraFact?: SourceFact<LocalAuroraActivity> }

function formatTime(instant: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(new Date(instant))
}

export function CloudForecastCard({ fact, timezone, night, highlightedPointTimes = [], auroraFact }: Props) {
  const { language, t } = useI18n()
  const data = fact.data
  const points = sortByValidAt((data?.cloudForecast ?? []).filter(point => !night || (Date.parse(point.validAt) >= Date.parse(night.evaluationWindowStartUtc) && Date.parse(point.validAt) < Date.parse(night.evaluationWindowEndUtc))))
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  return <section className="cloud-forecast-card" aria-labelledby="cloud-forecast-title" aria-live="polite">
    <div className="cloud-forecast-heading">
      <div><p className="eyebrow">{t('cloudForecast')}</p><h2 id="cloud-forecast-title">{t('cloudForecastTitle')}</h2></div>
      <a href={fact.sourceUrl} target="_blank" rel="noreferrer">{t('weatherSource')} ↗</a>
    </div>
    {fact.status === 'UNAVAILABLE' && <p className="cloud-forecast-message error">{t('sourceUnavailable')} {t(fact.failureCode === 'TIMEOUT' ? 'sourceTimeout' : fact.failureCode === 'RATE_LIMITED' ? 'sourceRateLimited' : fact.failureCode === 'FORBIDDEN' ? 'sourceForbidden' : 'sourceFailed')}</p>}
    {fact.status === 'NO_COVERAGE' && !data && <p className="cloud-forecast-message">{t('cloudNoCoverage')}</p>}
    {night && points.length === 0 && <p className="cloud-forecast-message">{t('cloudNoCoverage')}</p>}
    {night && <CloudDarknessTimeline points={data?.cloudForecast ?? []} night={night} timezone={timezone}
        highlightedPointTimes={highlightedPointTimes} auroraFact={auroraFact} />}
    {data && <>
      <details className="visual-details"><summary>{t('cloudDetails')}</summary><ul className="cloud-forecast-list">
        {points.map(point => <li key={point.validAt}>
          <time dateTime={point.validAt}>{formatTime(point.validAt, timezone, locale)}</time>
          <strong>{point.cloudCoverPercent === null ? t('cloudMissing') : `${point.cloudCoverPercent}%`}</strong>
        </li>)}
      </ul>
      {points.length === 0 && <p className="cloud-forecast-message">{t('cloudNoCoverage')}</p>}
      <dl className="cloud-forecast-meta">
        {fact.retrievedAtUtc && <div><dt>{t('dataRetrieved')}</dt><dd>{formatTime(fact.retrievedAtUtc, timezone, locale)}</dd></div>}
        {data.expiresAt && <div><dt>{t('forecastCacheExpires')}</dt><dd>{formatTime(data.expiresAt, timezone, locale)}</dd></div>}
      </dl>
      <p className="cloud-forecast-note">{t('cloudForecastNote')}</p>
      </details><p className="cloud-forecast-credit">{t('metNoAttribution')} <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a>. {t('metNoChanges')}</p>
    </>}
  </section>
}
