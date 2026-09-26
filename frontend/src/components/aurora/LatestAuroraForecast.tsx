import { useEffect, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import { transientRetryDelay } from '../../api/requestError'
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
  const date = new Date(instant)
  const localTime = new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
  const offset = new Intl.DateTimeFormat('en', {
    hour: '2-digit', timeZoneName: 'shortOffset',
  }).formatToParts(date).find(part => part.type === 'timeZoneName')?.value
  return offset ? `${localTime} (${offset})` : localTime
}

function nearbyPeriods(records: KpIndexRecord[]) {
  const now = Date.now()
  const reported = records
    .filter(record => record.type !== 'PREDICTED' && Date.parse(record.periodStart) <= now)
    .sort((left, right) => Date.parse(right.periodStart) - Date.parse(left.periodStart))[0]
  const upcoming = records
    .filter(record => record.type === 'PREDICTED' && Date.parse(record.periodStart) >= now)
    .sort((left, right) => Date.parse(left.periodStart) - Date.parse(right.periodStart))
    .slice(0, 6)
  return { reported, upcoming }
}

function geomagneticScaleForKp(kp: number, scaleNames: string[], belowG1: string, scale?: string) {
  const level = scale?.startsWith('G') ? Number(scale.slice(1)) : Math.min(5, Math.floor(kp))
  if (!Number.isFinite(level) || scale === 'BELOW_G1' || level < 1 || kp < 5) return belowG1
  return `G${level} · ${scaleNames[level - 1]}`
}

export function LatestAuroraForecast() {
  const { language, t } = useI18n()
  const [data, setData] = useState<KpIndexData | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  useEffect(() => {
    let controller: AbortController | null = null
    let retryTimer: number | undefined
    let retryAttempt = 0
    let hasLoadedData = false

    async function load(isRetry = false) {
      if (!isRetry) {
        window.clearTimeout(retryTimer)
        retryTimer = undefined
        retryAttempt = 0
      }
      if (!hasLoadedData) {
        setLoading(true)
        setError(null)
      }
      controller?.abort()
      const current = new AbortController()
      controller = current
      try {
        const response = await getKpIndex(current.signal)
        if (current.signal.aborted) return
        hasLoadedData = true
        setData(response)
        setError(null)
        setLoading(false)
        retryAttempt = 0
      } catch (cause) {
        if (current.signal.aborted) return
        setError(cause)
        setLoading(false)
        const delay = transientRetryDelay(cause, retryAttempt)
        if (delay !== null) {
          retryAttempt += 1
          retryTimer = window.setTimeout(() => {
            retryTimer = undefined
            void load(true)
          }, delay)
        }
      }
    }
    void load()
    const refresh = window.setInterval(() => { void load() }, 5 * 60 * 1000)
    return () => {
      window.clearInterval(refresh)
      window.clearTimeout(retryTimer)
      controller?.abort()
    }
  }, [])

  const forecast = data ? nextPredictedPeriod(data.records) : undefined
  const periods = data ? nearbyPeriods(data.records) : { reported: undefined, upcoming: [] }
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
      <div className="kp-trend">
        <div className="kp-trend-heading"><h3>{t('kpTrendTitle')}</h3><span>{t('kpTrendLocalTime')}</span></div>
        <div className="kp-trend-layout">
          {periods.reported && <div className="kp-latest-reported">
            <span>{t('latestReportedKp')}</span>
            <strong>Kp {periods.reported.kp.toFixed(2)}</strong>
            <small>{t(periods.reported.type === 'OBSERVED' ? 'observedKp' : 'estimatedKp')} · {formatLocalTime(periods.reported.periodStart, locale)}</small>
          </div>}
          <ol className="kp-period-list" aria-label={t('kpTrendTitle')}>
            {periods.upcoming.map(record => <li key={record.periodStart}>
              <div className="kp-period-meta"><time dateTime={record.periodStart}>{new Intl.DateTimeFormat(locale, { weekday: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(record.periodStart))}</time><strong>{record.kp.toFixed(1)}</strong></div>
              <small className="kp-period-scale">{t('noaaScaleLabel')}: {geomagneticScaleForKp(record.kp, t('noaaScaleName').split('|'), t('belowG1'), record.geomagneticStormScale)}</small>
              <span className="kp-period-track"><span style={{ width: `${Math.min(record.kp / 9, 1) * 100}%` }} /></span>
            </li>)}
          </ol>
        </div>
      </div>
    </div>}
  </section>
}
