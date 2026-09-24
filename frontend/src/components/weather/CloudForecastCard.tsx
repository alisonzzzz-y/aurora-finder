import type { WeatherForecast } from '../../types/weatherForecast'
import type { SourceFact } from '../../types/observationFacts'
import { useI18n } from '../../i18n'
import './CloudForecastCard.css'

type Props = { fact: SourceFact<WeatherForecast>; timezone: string }

function formatTime(instant: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(new Date(instant))
}

export function CloudForecastCard({ fact, timezone }: Props) {
  const { language, t } = useI18n()
  const data = fact.data
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  return <section className="cloud-forecast-card" aria-labelledby="cloud-forecast-title" aria-live="polite">
    <div className="cloud-forecast-heading">
      <div><p className="eyebrow">{t('cloudForecast')}</p><h2 id="cloud-forecast-title">{t('cloudForecastTitle')}</h2></div>
      <a href={fact.sourceUrl} target="_blank" rel="noreferrer">MET Norway ↗</a>
    </div>
    {fact.status === 'UNAVAILABLE' && <p className="cloud-forecast-message error">{t('sourceUnavailable')} {t(fact.failureCode === 'TIMEOUT' ? 'sourceTimeout' : fact.failureCode === 'RATE_LIMITED' ? 'sourceRateLimited' : fact.failureCode === 'FORBIDDEN' ? 'sourceForbidden' : 'sourceFailed')}</p>}
    {fact.status === 'NO_COVERAGE' && !data && <p className="cloud-forecast-message">{t('cloudNoCoverage')}</p>}
    {data && <>
      <ul className="cloud-forecast-list">
        {data.cloudForecast.slice(0, 8).map(point => <li key={point.validAt}>
          <time dateTime={point.validAt}>{formatTime(point.validAt, timezone, locale)}</time>
          <strong>{point.cloudCoverPercent === null ? t('cloudMissing') : `${point.cloudCoverPercent}%`}</strong>
        </li>)}
      </ul>
      {data.cloudForecast.length === 0 && <p className="cloud-forecast-message">{t('cloudNoCoverage')}</p>}
      <dl className="cloud-forecast-meta">
        {fact.retrievedAtUtc && <div><dt>{t('dataRetrieved')}</dt><dd>{formatTime(fact.retrievedAtUtc, timezone, locale)}</dd></div>}
        {data.expiresAt && <div><dt>{t('forecastCacheExpires')}</dt><dd>{formatTime(data.expiresAt, timezone, locale)}</dd></div>}
      </dl>
      <p className="cloud-forecast-note">{t('cloudForecastNote')}</p>
      <p className="cloud-forecast-credit">{t('metNoAttribution')} <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a>. {t('metNoChanges')}</p>
    </>}
  </section>
}
