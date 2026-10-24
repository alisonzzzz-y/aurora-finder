import { InfoHint } from '../InfoHint'
import { useEffect, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import { transientRetryDelay } from '../../api/requestError'
import type { KpIndexData } from '../../types/kpIndex'
import { selectKpPeriods } from './kpPeriods'
import { KpTrendChart } from './KpTrendChart'
import { TimeZonePicker } from './TimeZonePicker'
import { formatKpTime } from './kpChartData'
import { localizeError, useI18n } from '../../i18n'
import './LatestAuroraForecast.css'

export function LatestAuroraForecast() {
  const { language, t } = useI18n()
  const [data, setData] = useState<KpIndexData | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [timeZone, setTimeZone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone)
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

  const periods = selectKpPeriods(data?.records ?? [])
  const forecast = periods.forecast
  const levelKey = forecast
    ? forecast.activityLevel === 'LOW' ? 'activityLow'
      : forecast.activityLevel === 'MEDIUM' ? 'activityMedium' : 'activityHigh'
    : null

  return <section className="latest-aurora-forecast" aria-labelledby="latest-forecast-title">
    <div className="latest-forecast-heading">
      <div>

        <h2 id="latest-forecast-title">{t('globalKpActivity')}<InfoHint><p><a href="https://www.spaceweather.gov/products/planetary-k-index" target="_blank" rel="noreferrer">{t('noaaSource')} ↗</a></p>{data && <p>{t('dataRetrieved')}: {formatKpTime(data.retrievedAt, locale, timeZone)}</p>}</InfoHint></h2>
      </div>
      <TimeZonePicker value={timeZone} onChange={setTimeZone} language={language} />
    </div>
    {loading && <p className="latest-forecast-message" role="status">{t('loading')}</p>}
    {!loading && error !== null && <p className="latest-forecast-message error">{localizeError(error, t)}</p>}
    {!loading && error === null && !forecast && <p className="latest-forecast-message">{t('noUpcomingForecast')}</p>}
    {!loading && error === null && data && data.records.length > 0 && <div className="latest-forecast-content">
      {forecast && levelKey && <>
      <div className="latest-forecast-value">
        <span className="kp-value-with-info"><strong>Kp {forecast.kp.toFixed(2)}</strong><InfoHint><p>{t('globalKpNote')}</p><p>{t('geomagneticScaleNote')}</p></InfoHint></span>
        <span className={`activity-badge activity-${forecast.activityLevel.toLowerCase()}`}>
          {t(levelKey)}
        </span>
      </div>
      <div className="latest-forecast-meta">
        <div>
          <span>{t('forecastPeriod')}</span>
          <time dateTime={forecast.periodStart}>{t(forecast.type === 'ESTIMATED' ? 'estimatedKp' : forecast.type === 'OBSERVED' ? 'observedKp' : 'predictedKp')} · {formatKpTime(forecast.periodStart, locale, timeZone)}</time>
        </div>

      </div>
      </>}
      <div className="kp-trend">
        <div className="kp-trend-heading"><h3>{t('kpChartTitle')}<InfoHint>{t('kpChartInteraction')}</InfoHint></h3></div>
        <div className="kp-trend-layout">
          {periods.reported && <div className="kp-latest-reported">
            <span>{t('latestReportedKp')}</span>
            <span className="kp-value-with-info"><strong className="kp-featured-value"><span className="kp-featured-unit">Kp</span> <span className="kp-featured-number">{periods.reported.kp.toFixed(2)}</span></strong><InfoHint><p>{t('globalKpNote')}</p><p>{t('geomagneticScaleNote')}</p></InfoHint></span>
            <small>{t(periods.reported.type === 'OBSERVED' ? 'observedKp' : 'estimatedKp')} · {formatKpTime(periods.reported.periodStart, locale, timeZone)}</small>
          </div>}
          <KpTrendChart records={data?.records ?? []} timeZone={timeZone} />
        </div>
      </div>
    </div>}
  </section>
}
