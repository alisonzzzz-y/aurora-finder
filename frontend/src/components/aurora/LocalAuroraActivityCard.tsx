import type { LocalAuroraActivity } from '../../types/localAuroraActivity'
import type { SourceFact } from '../../types/observationFacts'
import { useI18n } from '../../i18n'
import './LocalAuroraActivityCard.css'

type Props = { fact: SourceFact<LocalAuroraActivity>; timezone: string }

function formatTime(instant: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: timezone,
  }).format(new Date(instant))
}

export function LocalAuroraActivityCard({ fact, timezone }: Props) {
  const { language, t } = useI18n()
  const data = fact.data
  const locale = language === 'zh' ? 'zh-CN' : 'en'

  const levelKey = data
    ? data.level === 'LOW' ? 'localActivityLow'
      : data.level === 'MEDIUM' ? 'localActivityMedium'
        : data.level === 'HIGH' ? 'localActivityHigh' : 'insufficientData'
    : undefined

  return <section className="local-aurora-card" aria-labelledby="local-aurora-title" aria-live="polite">
    <div className="local-aurora-heading">
      <div><h2 id="local-aurora-title">{t('localActivityTitle')}</h2></div>
    </div>
    <p className="forecast-source-note"><a href={fact.sourceUrl} target="_blank" rel="noreferrer">{t('noaaSource')} ↗</a></p>
    {fact.status === 'UNAVAILABLE' && <p className="local-aurora-message error">{t('sourceUnavailable')} {t(fact.failureCode === 'TIMEOUT' ? 'sourceTimeout' : fact.failureCode === 'RATE_LIMITED' ? 'sourceRateLimited' : fact.failureCode === 'FORBIDDEN' ? 'sourceForbidden' : 'sourceFailed')}</p>}
    {data && levelKey && <div className="local-aurora-content">
      <div className="local-aurora-value">
        <span className={`local-activity-badge local-activity-${data.level.toLowerCase()}`}>{t(levelKey)}</span>
        {data.modelValue !== null && <span>{t('noaaGridValue')} <strong>{data.modelValue}/100</strong></span>}
      </div>
      {fact.status === 'CURRENT' && data.status === 'CURRENT' && data.modelValue !== null && <div className="signal-ruler" role="meter" aria-label={t('noaaGridValue')} aria-valuemin={0} aria-valuemax={100} aria-valuenow={data.modelValue}><span style={{ left: `${data.modelValue}%` }} /><small>0</small><small>100</small></div>}
      <details className="visual-details"><summary>{t('nightDetails')}</summary><dl className="local-aurora-meta">
        <div><dt>{t('forecastValid')}</dt><dd>{formatTime(fact.sourceForecastAtUtc ?? data.forecastTime, timezone, locale)}</dd></div>
        <div><dt>{t('observed')}</dt><dd>{formatTime(fact.sourceObservedAtUtc ?? data.observationTime, timezone, locale)}</dd></div>
        <div><dt>{t('dataRetrieved')}</dt><dd>{formatTime(fact.retrievedAtUtc ?? data.retrievedAt, timezone, locale)}</dd></div>
      </dl>
      <p className="local-aurora-note">{t(data.status === 'EXPIRED' ? 'localAuroraExpiredNote' : 'localAuroraNote')}</p></details>
    </div>}
  </section>
}
