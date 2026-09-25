import type { NightOutlook } from '../../types/outlook'
import { useI18n } from '../../i18n'

type Props = { night: NightOutlook; index: number; timezone: string }

export function NightOutlookCard({ night, index, timezone }: Props) {
  const { language, t } = useI18n()
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const date = new Intl.DateTimeFormat(locale, {
    weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${night.localDate}T12:00:00Z`))
  const levelLabels: Record<NightOutlook['level'], string> = {
    HIGH: t('highLevel'),
    MEDIUM: t('mediumLevel'),
    LOW: t('lowLevel'),
    INSUFFICIENT_DATA: t('insufficientData'),
  }
  const thresholdLabels: Record<NightOutlook['solarDarkness']['thresholds'][number]['threshold'], string> = {
    CIVIL_TWILIGHT: t('civilTwilight'),
    NAUTICAL_TWILIGHT: t('nauticalTwilight'),
    ASTRONOMICAL_TWILIGHT: t('astronomicalTwilight'),
  }
  const reasonLabels: Record<NightOutlook['reasonCode'], string> = {
    RULES_NOT_VALIDATED: t('pendingReason'),
  }
  const statusLabels: Record<NightOutlook['solarDarkness']['thresholds'][number]['status'], string> = {
    INTERVALS_FOUND: t('solarIntervalsFound'),
    NO_INTERVAL: t('solarNoInterval'),
    CONTINUOUS: t('solarContinuous'),
    CALCULATION_FAILED: t('solarCalculationFailed'),
  }
  const localDateTime = (instant: string) => new Intl.DateTimeFormat(locale, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: timezone,
  }).format(new Date(instant))

  return <article className="night-card">
    <p className="night-index">{index === 0 ? t('tonight') : `${t('nightNumber')}${index + 1}${language === 'zh' ? '晚' : ''}`}</p>
    <h3>{date} <span className="utc-offset">(UTC{night.utcOffsetAtStart})</span></h3>
    <span className="level-badge">{levelLabels[night.level]}</span>
    <p>{reasonLabels[night.reasonCode ?? 'RULES_NOT_VALIDATED']}</p>
    <section className="solar-darkness" aria-label={t('solarDarknessTitle')}>
      <h4>{t('solarDarknessTitle')}</h4>
      {night.solarDarkness.thresholds.map((window) => <div className="solar-threshold" key={window.threshold}>
        <span>{thresholdLabels[window.threshold]} ({window.solarElevationDegrees}°)</span>
        <span>{window.intervals.length
          ? window.intervals.map((interval) => `${localDateTime(interval.startUtc)} – ${localDateTime(interval.endUtc)}`).join(', ')
          : statusLabels[window.status]}</span>
      </div>)}
      <p>{t('solarDarknessNote')}</p>
    </section>
  </article>
}
