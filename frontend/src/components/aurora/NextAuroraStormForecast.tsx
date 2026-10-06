import { InfoHint } from '../InfoHint'
import { StormComparisonChart, WarningTimeline } from './StormComparisonChart'
import { useEffect, useState } from 'react'
import { getGeomagneticStormForecast } from '../../api/geomagneticStorm'
import { getKpIndex } from '../../api/kpIndex'
import { getGeomagneticWarnings } from '../../api/geomagneticWarnings'
import { localizeError, useI18n } from '../../i18n'
import type { GeomagneticStormForecast } from '../../types/geomagneticStorm'
import type { KpIndexData, KpIndexRecord } from '../../types/kpIndex'
import type { GeomagneticWarnings } from '../../types/geomagneticWarnings'

function dayPeak(records: KpIndexRecord[], date: string) {
  return records.filter(record => record.type === 'PREDICTED' && record.periodStart.slice(0, 10) === date)
    .sort((a, b) => b.kp - a.kp)[0]
}

function localDateTime(value: string, locale: string, options: Intl.DateTimeFormatOptions) {
  return new Intl.DateTimeFormat(locale, options).format(new Date(value))
}

function kpPeriodRange(value: string, locale: string) {
  const start = new Date(value)
  const end = new Date(start.getTime() + 3 * 60 * 60 * 1000)
  const startLabel = new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' }).format(start)
  const endLabel = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' }).format(end)
  return `${startLabel}–${endLabel}`
}

export function NextAuroraStormForecast() {
  const { language, t } = useI18n()
  const [forecast, setForecast] = useState<GeomagneticStormForecast | null>(null)
  const [kp, setKp] = useState<KpIndexData | null>(null)
  const [warnings, setWarnings] = useState<GeomagneticWarnings | null>(null)
  const [warningsError, setWarningsError] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [now, setNow] = useState(() => Date.now())
  const locale = language === 'zh' ? 'zh-CN' : 'en-IE'

  useEffect(() => {
    let controller: AbortController | null = null
    async function load() {
      controller?.abort()
      const current = new AbortController()
      controller = current
      const results = await Promise.allSettled([
        getGeomagneticStormForecast(current.signal), getKpIndex(current.signal), getGeomagneticWarnings(current.signal),
      ])
      if (current.signal.aborted) return
      const [stormResult, kpResult, warningsResult] = results
      if (stormResult.status === 'fulfilled') { setForecast(stormResult.value); setError(null) }
      else { setForecast(null); setError(stormResult.reason) }
      setKp(kpResult.status === 'fulfilled' ? kpResult.value : null)
      if (warningsResult.status === 'fulfilled') { setWarnings(warningsResult.value); setWarningsError(false) }
      else { setWarnings(null); setWarningsError(true) }
      setNow(Date.now())
      setLoading(false)
    }
    void load()
    const refresh = window.setInterval(() => { void load() }, 2 * 60 * 1000)
    return () => { window.clearInterval(refresh); controller?.abort() }
  }, [])

  return <section className="storm-outlook" aria-labelledby="storm-outlook-title" aria-live="polite">
    <div className="storm-outlook-heading">
      <div><p className="eyebrow">{t('stormOutlookEyebrow')}</p><h2 id="storm-outlook-title">{t('stormOutlookTitle')}<InfoHint><p>{t('stormOutlookIntro')}</p><p>{t('geomagneticScaleNote')}</p></InfoHint></h2></div>
    </div>
    <p className="forecast-source-note"><a href="https://services.swpc.noaa.gov/text/3-day-geomag-forecast.txt" target="_blank" rel="noreferrer">{t('noaaSource')} ↗</a></p>
    {warningsError && <p className="storm-outlook-message">{t('geomagneticWarningsUnavailable')}</p>}
    {!warningsError && warnings && <div className="geomagnetic-warning-list" aria-label={t('geomagneticWarningsTitle')}>
      <h3>{t('geomagneticWarningsTitle')}</h3>
      <p className="geomagnetic-warning-checked">{t('warningChecked')}: {localDateTime(warnings.retrievedAt, locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })}</p>
      {warnings.warnings.length === 0 && <p>{t('noActiveGeomagneticWarnings')}</p>}
      <div className="storm-watch">
        <h4>{t('stormWatchTitle')}<InfoHint>{t('stormWatchExplanation')}</InfoHint></h4>
        {(warnings.stormWatchDays ?? []).length === 0
          ? <p>{t('stormWatchNone')}</p>
          : <ul>{(warnings.stormWatchDays ?? []).map(day => {
            const date = new Date(`${day.date}T12:00:00Z`)
            const scaleNames = t('noaaScaleName').split('|')
            const level = day.noaaScale ? Number(day.noaaScale.slice(1)) : 0
            return <li key={day.date}>
              <time dateTime={day.date}>{new Intl.DateTimeFormat(locale, { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' }).format(date)}</time>
              <strong>{day.noaaScale ? `${day.noaaScale} · ${scaleNames[level - 1]}` : t('belowG1')}</strong>
            </li>
          })}</ul>}
      </div>
      <WarningTimeline warnings={warnings.warnings} now={now} />
      <details className="visual-details"><summary>{t('warningValidWindow')}</summary>{warnings.warnings.map(warning => <article key={`${warning.productId}:${warning.validFrom}`}>
        <strong>{warning.noaaScale ?? `${t('expectedKIndex')}: ${warning.expectedKIndex}`}</strong>
        <span>{t('warningValidWindow')}: {localDateTime(warning.validFrom, locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })}–{localDateTime(warning.validTo, locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })}</span>
      </article>)}</details>
    </div>}
    {loading && <p className="storm-outlook-message">{t('loading')}</p>}
    {!loading && error !== null && <p className="storm-outlook-message error">{localizeError(error, t)}</p>}
    {!loading && forecast && <>
      <StormComparisonChart days={forecast.days} />
      <details className="visual-details"><summary>{t('kpPeak')}</summary>{forecast.days.map(day => {
        const peak = kp ? dayPeak(kp.records, day.date) : undefined
        return <p key={day.date}>{day.date} (UTC): {peak ? <>Kp {peak.kp.toFixed(2)} · {kpPeriodRange(peak.periodStart, locale)}</> : t('kpPeakUnavailable')}</p>
      })}</details>
      <p className="storm-outlook-meta">{t('forecastIssued')}: {localDateTime(forecast.issuedAt, locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })} · {t('dataRetrieved')}: {localDateTime(forecast.retrievedAt, locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })}</p>
    </>}
    <details className="visual-details"><summary>{t('bestViewingPlacesTitle')}</summary><div className="storm-best-places">
      <h3>{t('bestViewingPlacesTitle')}</h3>
      <p>{t('bestViewingPlacesPending')}</p>
    </div>
    </details><p className="storm-outlook-note">{t('stormOutlookLimit')}</p>
  </section>
}
