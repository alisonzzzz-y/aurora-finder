import type { NightOutlook } from '../../types/outlook'
import { localizeReason, useI18n } from '../../i18n'

type Props = { night: NightOutlook; index: number }

export function NightOutlookCard({ night, index }: Props) {
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

  return <article className="night-card">
    <p className="night-index">{index === 0 ? t('tonight') : `${t('nightNumber')}${index + 1}${language === 'zh' ? '晚' : ''}`}</p>
    <h3>{date} <span className="utc-offset">(UTC{night.utcOffsetAtStart})</span></h3>
    <span className="level-badge">{levelLabels[night.level]}</span>
    <p>{localizeReason(night.reason, t)}</p>
  </article>
}
