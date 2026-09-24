import { useEffect, useState } from 'react'
import { getLocalAuroraActivity } from '../../api/localAuroraActivity'
import type { LocalAuroraActivity } from '../../types/localAuroraActivity'
import { localizeError, useI18n } from '../../i18n'
import './LocalAuroraActivityCard.css'

type Props = { latitude: number; longitude: number; timezone: string }

function formatTime(instant: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(new Date(instant))
}

export function LocalAuroraActivityCard({ latitude, longitude, timezone }: Props) {
  const { language, t } = useI18n()
  const [data, setData] = useState<LocalAuroraActivity | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  useEffect(() => {
    let activeRequest: AbortController | null = null
    async function load() {
      activeRequest?.abort()
      const controller = new AbortController()
      activeRequest = controller
      try {
        setData(await getLocalAuroraActivity(latitude, longitude, controller.signal))
        setError(null)
      } catch (cause) {
        if (controller.signal.aborted || activeRequest !== controller) return
        setError(cause)
      } finally {
        if (!controller.signal.aborted && activeRequest === controller) setLoading(false)
      }
    }
    void load()
    const interval = window.setInterval(() => { void load() }, 5 * 60 * 1000)
    return () => {
      window.clearInterval(interval)
      activeRequest?.abort()
    }
  }, [latitude, longitude])

  const levelKey = data
    ? data.level === 'LOW' ? 'localActivityLow'
      : data.level === 'MEDIUM' ? 'localActivityMedium'
        : data.level === 'HIGH' ? 'localActivityHigh' : 'insufficientData'
    : undefined

  return <section className="local-aurora-card" aria-labelledby="local-aurora-title" aria-live="polite">
    <div className="local-aurora-heading">
      <div><p className="eyebrow">{t('localAuroraForecast')}</p><h2 id="local-aurora-title">{t('localActivityTitle')}</h2></div>
      <a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA SWPC ↗</a>
    </div>
    {loading && <p className="local-aurora-message">{t('loading')}</p>}
    {!loading && error !== null && <p className="local-aurora-message error">{localizeError(error, t)}</p>}
    {!loading && error === null && data && levelKey && <div className="local-aurora-content">
      <div className="local-aurora-value">
        <span className={`local-activity-badge local-activity-${data.level.toLowerCase()}`}>{t(levelKey)}</span>
        <span>{t('noaaGridValue')} <strong>{data.modelValue}/100</strong></span>
      </div>
      <dl className="local-aurora-meta">
        <div><dt>{t('forecastValid')}</dt><dd>{formatTime(data.forecastTime, timezone, locale)}</dd></div>
        <div><dt>{t('observed')}</dt><dd>{formatTime(data.observationTime, timezone, locale)}</dd></div>
      </dl>
      <p className="local-aurora-note">{t('localAuroraNote')}</p>
    </div>}
  </section>
}
