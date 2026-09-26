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
      else setError(stormResult.reason)
      if (kpResult.status === 'fulfilled') setKp(kpResult.value)
      if (warningsResult.status === 'fulfilled') { setWarnings(warningsResult.value); setWarningsError(false) }
      else setWarningsError(true)
      setLoading(false)
    }
    void load()
    const refresh = window.setInterval(() => { void load() }, 2 * 60 * 1000)
    return () => { window.clearInterval(refresh); controller?.abort() }
  }, [])

  return <section className="storm-outlook" aria-labelledby="storm-outlook-title" aria-live="polite">
    <div className="storm-outlook-heading">
      <div><p className="eyebrow">{t('stormOutlookEyebrow')}</p><h2 id="storm-outlook-title">{t('stormOutlookTitle')}</h2></div>
      <a href="https://services.swpc.noaa.gov/text/3-day-geomag-forecast.txt" target="_blank" rel="noreferrer">NOAA ↗</a>
    </div>
    <p className="storm-outlook-intro">{t('stormOutlookIntro')}</p>
    {warningsError && <p className="storm-outlook-message">{t('geomagneticWarningsUnavailable')}</p>}
    {!warningsError && warnings && <div className="geomagnetic-warning-list" aria-label={t('geomagneticWarningsTitle')}>
      <h3>{t('geomagneticWarningsTitle')}</h3>
      <p className="geomagnetic-warning-checked">{t('warningChecked')}: {localDateTime(warnings.retrievedAt, locale, { dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'short' })}</p>
      {warnings.warnings.length === 0 && <p>{t('noActiveGeomagneticWarnings')}</p>}
      {warnings.warnings.map(warning => <article key={`${warning.productId}:${warning.validFrom}`}>
        <strong>{warning.noaaScale ?? `K-index ${warning.expectedKIndex}`}</strong>
        <span>{t('warningValidWindow')}: {localDateTime(warning.validFrom, locale, { dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'short' })}–{localDateTime(warning.validTo, locale, { dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'short' })}</span>
      </article>)}
    </div>}
    {loading && <p className="storm-outlook-message">{t('loading')}</p>}
    {!loading && error !== null && <p className="storm-outlook-message error">{localizeError(error, t)}</p>}
    {!loading && forecast && <>
      <div className="storm-day-list">
        {forecast.days.map(day => {
          const peak = kp ? dayPeak(kp.records, day.date) : undefined
          const date = new Date(`${day.date}T12:00:00Z`)
          return <article className="storm-day" key={day.date}>
            <h3>{new Intl.DateTimeFormat(locale, { weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC' }).format(date)}</h3>
            <dl className="storm-probabilities">
              <div><dt>{t('activeGeomagnetic')}</dt><dd>{day.activeChancePercent}%</dd></div>
              <div><dt>{t('minorStorm')}</dt><dd>{day.minorStormChancePercent}%</dd></div>
              <div><dt>{t('moderateStorm')}</dt><dd>{day.moderateStormChancePercent}%</dd></div>
              <div><dt>{t('strongStorm')}</dt><dd>{day.strongExtremeStormChancePercent}%</dd></div>
            </dl>
            <p className="storm-kp-peak">{peak
              ? <>{t('kpPeak')}: <strong>Kp {peak.kp.toFixed(2)}</strong> · {kpPeriodRange(peak.periodStart, locale)}</>
              : t('kpPeakUnavailable')}</p>
          </article>
        })}
      </div>
      <p className="storm-outlook-meta">{t('forecastIssued')}: {localDateTime(forecast.issuedAt, locale, { dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'short' })} · {t('dataRetrieved')}: {localDateTime(forecast.retrievedAt, locale, { dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'short' })}</p>
    </>}
    <div className="storm-best-places">
      <h3>{t('bestViewingPlacesTitle')}</h3>
      <p>{t('bestViewingPlacesPending')}</p>
    </div>
    <p className="storm-outlook-note">{t('stormOutlookLimit')}</p>
  </section>
}
