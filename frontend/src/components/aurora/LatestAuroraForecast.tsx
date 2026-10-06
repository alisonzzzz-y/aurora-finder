import { InfoHint } from '../InfoHint'
import { useEffect, useRef, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import { transientRetryDelay } from '../../api/requestError'
import type { KpIndexData } from '../../types/kpIndex'
import { canConnectKpPeriods, selectKpPeriods } from './kpPeriods'
import { localizeError, useI18n } from '../../i18n'
import './LatestAuroraForecast.css'

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

function formatAxisTime(instant: string, locale: string) {
  return new Intl.DateTimeFormat(locale, { weekday: 'short', hour: '2-digit' }).format(new Date(instant))
}

function geomagneticScaleForKp(kp: number, scaleNames: string[], belowG1: string, scale?: string) {
  if (scale === 'BELOW_G1' || kp < 5) return belowG1
  const level = scale?.startsWith('G') ? Number(scale.slice(1)) : Math.min(5, Math.floor(kp) - 4)
  if (!Number.isFinite(level) || level < 1) return belowG1
  return `G${level} · ${scaleNames[level - 1]}`
}

export function LatestAuroraForecast() {
  const { language, t } = useI18n()
  const [data, setData] = useState<KpIndexData | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const chartRef = useRef<HTMLDivElement>(null)
  const [chartWidth, setChartWidth] = useState(640)
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
  const hasKpGap = periods.trend.slice(0, -1).some((record, index) =>
    !canConnectKpPeriods(record, periods.trend[index + 1]))
  useEffect(() => {
    const element = chartRef.current
    if (!element) return
    const observer = new ResizeObserver(entries => {
      const width = entries[0]?.contentRect.width
      if (width) setChartWidth(width)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [forecast])

  const chartHeight = 320
  const chartPadding = { top: 14, right: 14, bottom: 34, left: 34 }
  const chartStart = chartPadding.left
  const chartEnd = Math.max(chartStart + 1, chartWidth - chartPadding.right)
  const chartTop = chartPadding.top
  const chartBottom = chartHeight - chartPadding.bottom
  const chartTimes = periods.trend.map(record => Date.parse(record.periodStart))
  const timeStart = chartTimes.length ? Math.min(...chartTimes) : 0
  const timeEnd = chartTimes.length ? Math.max(...chartTimes) : timeStart + 1
  const xFor = (instant: string) => chartStart + (Date.parse(instant) - timeStart) / Math.max(1, timeEnd - timeStart) * (chartEnd - chartStart)
  const yFor = (kp: number) => chartTop + (9 - kp) / 9 * (chartBottom - chartTop)
  const yTicks = [0, 3, 5, 6, 9]
  const labelStride = Math.max(1, Math.ceil(periods.trend.length / 5))
  const chartDescription = t('kpChartDescription')
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
    </div>
    <p className="forecast-source-note"><a href="https://www.spaceweather.gov/products/planetary-k-index" target="_blank" rel="noreferrer">{t('noaaSource')} ↗</a></p>
    {loading && <p className="latest-forecast-message">{t('loading')}</p>}
    {!loading && error !== null && <p className="latest-forecast-message error">{localizeError(error, t)}</p>}
    {!loading && error === null && !forecast && <p className="latest-forecast-message">{t('noUpcomingForecast')}</p>}
    {!loading && error === null && forecast && levelKey && <div className="latest-forecast-content">
      <div className="latest-forecast-value">
        <span className="kp-value-with-info"><strong>Kp {forecast.kp.toFixed(2)}</strong><InfoHint><p>{t('globalKpNote')}</p><p>{t('geomagneticScaleNote')}</p></InfoHint></span>
        <span className={`activity-badge activity-${forecast.activityLevel.toLowerCase()}`}>
          {t(levelKey)}
        </span>
      </div>
      <div className="latest-forecast-meta">
        <div>
          <span>{t('forecastPeriod')}</span>
          <time dateTime={forecast.periodStart}>{t(forecast.type === 'ESTIMATED' ? 'estimatedKp' : forecast.type === 'OBSERVED' ? 'observedKp' : 'predictedKp')} · {formatLocalTime(forecast.periodStart, locale)}</time>
        </div>
        {data && <div>
          <span>{t('dataRetrieved')}</span>
          <time dateTime={data.retrievedAt}>{formatLocalTime(data.retrievedAt, locale)}</time>
        </div>}
      </div>
      <div className="kp-trend">
        <div className="kp-trend-heading"><h3>{t('kpChartTitle')}</h3><span>{t('kpTrendLocalTime')}</span></div>
        <div className="kp-trend-layout">
          {periods.reported && <div className="kp-latest-reported">
            <span>{t('latestReportedKp')}</span>
            <span className="kp-value-with-info"><strong>Kp {periods.reported.kp.toFixed(2)}</strong><InfoHint><p>{t('globalKpNote')}</p><p>{t('geomagneticScaleNote')}</p></InfoHint></span>
            <small>{t(periods.reported.type === 'OBSERVED' ? 'observedKp' : 'estimatedKp')} · {formatLocalTime(periods.reported.periodStart, locale)}</small>
          </div>}
          <div className="kp-chart-area">
            <div className="kp-chart-legend" aria-hidden="true">
              <span><i className="observed" />{t('observedKp')}</span>
              <span><i className="estimated" />{t('estimatedKp')}</span>
              <span><i className="predicted" />{t('predictedKp')}</span>
              {hasKpGap && <span><i className="gap" />{t('kpChartGapLegend')}</span>}
            </div>
            <div ref={chartRef} className="kp-chart-wrap">
              {periods.trend.length > 0 ? <svg className="kp-chart" viewBox={`0 0 ${chartWidth} ${chartHeight}`} role="img" aria-label={`${t('kpChartTitle')}. ${chartDescription}`}>
                <title>{t('kpChartTitle')}</title>
                <desc>{chartDescription}</desc>
                {yTicks.map(tick => <g key={tick}>
                  <line className={tick === 5 ? 'kp-gridline kp-threshold' : 'kp-gridline'} x1={chartStart} x2={chartEnd} y1={yFor(tick)} y2={yFor(tick)} />
                  <text className="kp-axis-label" x={chartStart - 9} y={yFor(tick) + 4} textAnchor="end">{tick}</text>
                </g>)}
                <text className="kp-threshold-label" x={chartEnd} y={yFor(5) - 6} textAnchor="end">{t('kpThresholdLabel')}</text>
                {periods.trend.slice(0, -1).map((record, index) => {
                  const next = periods.trend[index + 1]
                  if (!canConnectKpPeriods(record, next)) return null
                  return <path key={`${record.periodStart}-${next.periodStart}`} className={`kp-chart-segment ${record.type.toLowerCase()}`}
                    d={`M ${xFor(record.periodStart)} ${yFor(record.kp)} L ${xFor(next.periodStart)} ${yFor(next.kp)}`} />
                })}
                {periods.trend.map((record, index) => {
                  const x = xFor(record.periodStart)
                  const y = yFor(record.kp)
                  const stormScale = geomagneticScaleForKp(record.kp, t('noaaScaleName').split('|'), t('belowG1'), record.geomagneticStormScale)
                  return <g key={record.periodStart} className={`kp-chart-mark ${record.type.toLowerCase()}`}>
                    {record.type === 'ESTIMATED'
                      ? <rect x={x - 4} y={y - 4} width="8" height="8" transform={`rotate(45 ${x} ${y})`} />
                      : <circle cx={x} cy={y} r="4" />}
                    <title>{`${formatLocalTime(record.periodStart, locale)} · ${t(record.type === 'OBSERVED' ? 'observedKp' : record.type === 'ESTIMATED' ? 'estimatedKp' : 'predictedKp')} · Kp ${record.kp.toFixed(2)} · ${t('noaaScaleLabel')}: ${stormScale}`}</title>
                    {index % labelStride === 0 || index === periods.trend.length - 1
                      ? <text className="kp-time-label" x={x} y={chartHeight - 9} textAnchor="middle">{formatAxisTime(record.periodStart, locale)}</text>
                      : null}
                  </g>
                })}
              </svg> : <p className="latest-forecast-message">{t('kpChartNoData')}</p>}
            </div>
            <ol className="sr-only">
              {periods.trend.map(record => <li key={`summary-${record.periodStart}`}>{formatLocalTime(record.periodStart, locale)} · {t(record.type === 'OBSERVED' ? 'observedKp' : record.type === 'ESTIMATED' ? 'estimatedKp' : 'predictedKp')} · Kp {record.kp.toFixed(2)}</li>)}
            </ol>
          </div>
        </div>
      </div>
    </div>}
  </section>
}
