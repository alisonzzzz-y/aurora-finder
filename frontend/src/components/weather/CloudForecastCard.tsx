import { useEffect, useState } from 'react'
import { getWeatherForecast } from '../../api/weatherForecast'
import type { WeatherForecast } from '../../types/weatherForecast'
import { localizeError, useI18n } from '../../i18n'
import './CloudForecastCard.css'

type Props = { latitude: number; longitude: number; timezone: string }

function formatTime(instant: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(new Date(instant))
}

export function CloudForecastCard({ latitude, longitude, timezone }: Props) {
  const { language, t } = useI18n()
  const requestKey = `${latitude}|${longitude}|${timezone}`
  const [state, setState] = useState<{
    requestKey: string
    data: WeatherForecast | null
    error: unknown
    loading: boolean
  }>({ requestKey: '', data: null, error: null, loading: true })
  const view = state.requestKey === requestKey
    ? state
    : { requestKey, data: null, error: null, loading: true }
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  useEffect(() => {
    const controller = new AbortController()
    getWeatherForecast(latitude, longitude, timezone, controller.signal)
      .then(data => { if (!controller.signal.aborted) setState({ requestKey, data, error: null, loading: false }) })
      .catch(error => { if (!controller.signal.aborted) setState({ requestKey, data: null, error, loading: false }) })
    return () => controller.abort()
  }, [latitude, longitude, timezone, requestKey])

  return <section className="cloud-forecast-card" aria-labelledby="cloud-forecast-title" aria-live="polite">
    <div className="cloud-forecast-heading">
      <div><p className="eyebrow">{t('cloudForecast')}</p><h2 id="cloud-forecast-title">{t('cloudForecastTitle')}</h2></div>
      <a href="https://api.met.no/" target="_blank" rel="noreferrer">MET Norway ↗</a>
    </div>
    {view.loading && <p className="cloud-forecast-message">{t('loading')}</p>}
    {!view.loading && view.error !== null && <p className="cloud-forecast-message error">{localizeError(view.error, t)}</p>}
    {!view.loading && view.error === null && view.data && <>
      <ul className="cloud-forecast-list">
        {view.data.cloudForecast.slice(0, 8).map(point => <li key={point.validAt}>
          <time dateTime={point.validAt}>{formatTime(point.validAt, timezone, locale)}</time>
          <strong>{point.cloudCoverPercent === null ? t('cloudMissing') : `${point.cloudCoverPercent}%`}</strong>
        </li>)}
      </ul>
      {view.data.cloudForecast.length === 0 && <p className="cloud-forecast-message">{t('cloudNoCoverage')}</p>}
      <dl className="cloud-forecast-meta">
        <div><dt>{t('dataRetrieved')}</dt><dd>{formatTime(view.data.retrievedAt, timezone, locale)}</dd></div>
        <div><dt>{t('forecastCacheExpires')}</dt><dd>{formatTime(view.data.expiresAt, timezone, locale)}</dd></div>
      </dl>
      <p className="cloud-forecast-note">{t('cloudForecastNote')}</p>
      <p className="cloud-forecast-credit">{t('metNoAttribution')} <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a>. {t('metNoChanges')}</p>
    </>}
  </section>
}
