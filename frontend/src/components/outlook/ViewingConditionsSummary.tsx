import { useEffect, useState } from 'react'
import type { ObservationFacts } from '../../types/observationFacts'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'

export function ViewingConditionsSummary({ facts }: { facts: ObservationFacts }) {
  const { t, language } = useI18n()
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30000)
    return () => window.clearInterval(timer)
  }, [])
  const data = facts.viewingConditions
  const current = data?.ruleVersion === 'sky-conditions-v2' && data.validUntilUtc && Date.parse(data.validUntilUtc) > now
  const activity = facts.auroraActivity
  const localCurrent = activity.status === 'CURRENT' && activity.data?.status === 'CURRENT'
    && activity.data.forecastTime && Date.parse(activity.data.forecastTime) > now
  const localValue = localCurrent ? activity.data?.modelValue : null
  const time = (value: string) => new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en', {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:facts.outlook.location.timezone,timeZoneName:'short'}).format(new Date(value))
  const horizon = data?.horizonAurora
  const horizonCurrent = horizon?.forecastTime && Date.parse(horizon.forecastTime) > now
  const level = current ? data.level : 'INSUFFICIENT_DATA'
  const labels = { HIGH: 'conditionsHigh', MEDIUM: 'conditionsMedium', LOW: 'conditionsLow', INSUFFICIENT_DATA: 'conditionsUnknown' } as const
  const reasons: Record<string, string> = {
    NOT_DARK: t('conditionsNotDark'), CLOUDY: t('conditionsCloudy'), WEAK_SIGNAL: t('conditionsWeak'),
    FAVOURABLE_FACTORS: t('conditionsFavourable'), MIXED_FACTORS: t('conditionsMixed'),
    AURORA_UNAVAILABLE: t('conditionsNoAurora'), CLOUD_UNAVAILABLE: t('conditionsNoCloud'),
    NO_SHARED_TIME: t('conditionsNoOverlap'), NO_CURRENT_CLOUD_SAMPLE: t('conditionsNoCloud'), DARKNESS_UNAVAILABLE: t('conditionsNoDarkness'),
  }
  return <div className="viewing-conditions">
    <div className="conditions-heading">{t('conditionsTitle')}<InfoHint><p>{t('conditionsRules')}</p><p>{t('conditionsProbability')}</p></InfoHint></div>
    <strong className={`conditions-level conditions-${level.toLowerCase()}`}>{t(labels[level])}</strong>
    <p>{data && (current || data.level === 'INSUFFICIENT_DATA') ? data.reasons.map(reason => reasons[reason] ?? t('conditionsUnknown')).join(' · ') : t('conditionsRefresh')}</p>
    {current && data.evaluatedAtUtc && <p><time>{new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en', {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:facts.outlook.location.timezone,timeZoneName:'short'}).format(new Date(data.evaluatedAtUtc))}</time> · {t('conditionsSample')}</p>}
    {current && <div className="conditions-factors"><span>{t('cloudCoverLegend')} {data.cloudPercent}%</span><span>{data.dark ? t('conditionsDark') : t('conditionsNotDark')}</span></div>}
    <div className="horizon-activity">
      <div className="conditions-heading">{t('nearbyActivityShort')}<InfoHint>{t('localAuroraNote')}</InfoHint></div>
      <strong>{localValue == null ? t('conditionsNoAurora') : `${t(localValue < 18 ? 'localActivityLow' : localValue < 50 ? 'localActivityMedium' : 'localActivityHigh')} · ${localValue}/100`}</strong>
    </div>
    <div className="horizon-activity">
      <div className="conditions-heading">{t('horizonTitle')}<InfoHint>{t('horizonRules')}</InfoHint></div>
      <strong>{t(horizonCurrent && horizon?.status === 'CANDIDATE' ? 'horizonCandidate' : horizonCurrent && horizon?.status === 'NO_CANDIDATE' ? 'horizonNone' : 'horizonUnavailable')}</strong>
      {horizonCurrent && horizon?.forecastTime && <p><time>{time(horizon.forecastTime)}</time></p>}
      {horizonCurrent && horizon?.status === 'CANDIDATE' && <p>{t(horizon.direction === 'SOUTH' ? 'horizonSouth' : 'horizonNorth')} · {horizon.distanceKm} km · {t('horizonSignal')} {horizon.modelSignal}/100</p>}
    </div>
  </div>
}
