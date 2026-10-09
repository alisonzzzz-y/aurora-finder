import { InfoHint } from '../InfoHint'
import { useId } from 'react'
import type { NightOutlook } from '../../types/outlook'
import { useI18n } from '../../i18n'
import { darknessHours, localNightPosition } from './darknessTimeline'
import './VisualOutlook.css'

type Props = { nights: NightOutlook[]; timezone: string; selectedIndex: number; onSelect: (index: number) => void }
const thresholds = ['CIVIL_TWILIGHT', 'NAUTICAL_TWILIGHT', 'ASTRONOMICAL_TWILIGHT'] as const

export function NightDarknessChart({ nights, timezone, selectedIndex, onSelect }: Props) {
  const { language, t } = useI18n()
  const id = useId()
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const dateLabel = (night: NightOutlook) => new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(`${night.localDate}T12:00:00Z`))
  const timeLabel = (instant: string) => new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', timeZone: timezone }).format(new Date(instant))
  return <section className="night-comparison" aria-labelledby={id}>
    <h3 id={id}>{t('threeNightDarkness')}<InfoHint>{t('nightComparisonNote')}</InfoHint></h3>
    <div className="night-comparison-axis" aria-hidden="true">{['12:00', '18:00', '00:00', '06:00', '12:00'].map((label, index) => <span key={index}>{label}</span>)}</div>
    <div className="night-comparison-rows">
      {nights.map((night, index) => {
        const deepest = night.solarDarkness.thresholds.find(item => item.threshold === 'ASTRONOMICAL_TWILIGHT')
        const duration = deepest && deepest.status !== 'CALCULATION_FAILED' ? darknessHours(deepest.intervals) : null
        return <button type="button" className={`night-comparison-row${selectedIndex === index ? ' is-selected' : ''}`} key={night.localDate} aria-pressed={selectedIndex === index} onClick={() => onSelect(index)}>
          <span className="night-row-heading"><strong>{index === 0 ? t('tonight') : dateLabel(night)}</strong><span>{dateLabel(night)} · UTC{night.utcOffsetAtStart}</span></span>
          <span className="night-time-track" aria-label={`${dateLabel(night)}. ${t('localTimeNote')}`}>
            {thresholds.map((key, depth) => night.solarDarkness.thresholds.find(item => item.threshold === key)?.intervals.map(interval => {
              const left = localNightPosition(interval.startUtc, night, timezone)
              const right = localNightPosition(interval.endUtc, night, timezone)
              const label = t(depth === 0 ? 'civilTwilightShort' : depth === 1 ? 'nauticalTwilightShort' : 'astronomicalTwilightShort')
              return right > left ? <span key={`${key}-${interval.startUtc}`} className={`night-time-band night-depth-${depth}`} style={{ left: `${left / 24 * 100}%`, width: `${(right - left) / 24 * 100}%` }} title={`${label}: ${timeLabel(interval.startUtc)} - ${timeLabel(interval.endUtc)}`} /> : null
            }))}
          </span>
          <span className="night-duration">{duration === null ? t('solarCalculationFailed') : duration === 0 ? t('solarNoInterval') : `${t('deepDarkness')} ${duration.toFixed(1)} ${t('hoursShort')}`}</span>
        </button>
      })}
    </div>
    <div className="night-depth-legend">{thresholds.map((key, index) => <span key={key}><i className={`night-depth-${index}`} />{t(index === 0 ? 'civilTwilightShort' : index === 1 ? 'nauticalTwilightShort' : 'astronomicalTwilightShort')}</span>)}</div>
  </section>
}
