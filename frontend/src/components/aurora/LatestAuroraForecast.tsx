import { useEffect, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import type { KpIndexData, KpIndexRecord } from '../../types/kpIndex'
import { localizeError, useI18n } from '../../i18n'
import './LatestAuroraForecast.css'

function nextPredictedPeriod(records: KpIndexRecord[]) {
  const now = Date.now()
  return records
    .filter(record => record.type === 'PREDICTED' && Date.parse(record.periodStart) >= now)
    .sort((left, right) => Date.parse(left.periodStart) - Date.parse(right.periodStart))[0]
}

function formatLocalTime(instant: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'shortOffset',
  }).format(new Date(instant))
}

export function LatestAuroraForecast() {
  const { language, t } = useI18n()
  const [data, setData] = useState<KpIndexData | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        setData(await getKpIndex(controller.signal))
        setError(null)
      } catch (cause) {
        if (controller.signal.aborted) return
        setError(cause)
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    void load()
    const refresh = window.setInterval(() => { void load() }, 5 * 60 * 1000)
    return () => {
      window.clearInterval(refresh)
      controller.abort()
    }
  }, [])

  const forecast = data ? nextPredictedPeriod(data.records) : undefined
  const levelKey = forecast
    ? forecast.activityLevel === 'LOW' ? 'activityLow'
      : forecast.activityLevel === 'MEDIUM' ? 'activityMedium' : 'activityHigh'
    : null

  return <section className="latest-aurora-forecast" aria-labelledby="latest-forecast-title" aria-live="polite">
    <div className="latest-forecast-heading">
      <div>
        <p className="eyebrow">{t('latestForecast')}</p>
        <h2 id="latest-forecast-title">{t('globalKpActivity')}</h2>
      </div>
      <a href="https://www.spaceweather.gov/content/tips-viewing-aurora" target="_blank" rel="noreferrer">
        NOAA SWPC ↗
      </a>
    </div>
    {loading && <p className="latest-forecast-message">{t('loading')}</p>}
    {!loading && error !== null && <p className="latest-forecast-message error">{localizeError(error, t)}</p>}
    {!loading && error === null && !forecast && <p className="latest-forecast-message">{t('noUpcomingForecast')}</p>}
    {!loading && error === null && forecast && levelKey && <div className="latest-forecast-content">
      <div className="latest-forecast-value">
        <strong>Kp {forecast.kp.toFixed(2)}</strong>
        <span className={`activity-badge activity-${forecast.activityLevel.toLowerCase()}`}>
          {t(levelKey)}
        </span>
      </div>
      <div className="latest-forecast-meta">
        <div>
          <span>{t('forecastPeriod')}</span>
          <time dateTime={forecast.periodStart}>{formatLocalTime(forecast.periodStart, locale)}</time>
        </div>
        {data && <div>
          <span>{t('dataRetrieved')}</span>
          <time dateTime={data.retrievedAt}>{formatLocalTime(data.retrievedAt, locale)}</time>
        </div>}
      </div>
      <p className="latest-forecast-note">{t('globalKpNote')}</p>
    </div>}
  </section>
}
