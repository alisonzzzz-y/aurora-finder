import type { NightOutlook } from '../../types/outlook'

type Props = { night: NightOutlook; index: number }

const levelLabels: Record<NightOutlook['level'], string> = {
  HIGH: 'High outlook level',
  MEDIUM: 'Medium outlook level',
  LOW: 'Low outlook level',
  INSUFFICIENT_DATA: 'Insufficient data',
}

export function NightOutlookCard({ night, index }: Props) {
  const date = new Intl.DateTimeFormat('en', {
    weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${night.localDate}T12:00:00Z`))

  return <article className="night-card">
    <p className="night-index">{index === 0 ? 'Tonight' : `Night ${index + 1}`}</p>
    <h3>{date} <span className="utc-offset">(UTC{night.utcOffsetAtStart})</span></h3>
    <span className="level-badge">{levelLabels[night.level]}</span>
    <p>{night.reason}</p>
  </article>
}
