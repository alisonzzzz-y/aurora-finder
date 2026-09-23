import type { NightOutlook } from '../../types/outlook'

type Props = { night: NightOutlook; index: number }

export function NightOutlookCard({ night, index }: Props) {
  const date = new Intl.DateTimeFormat('en', {
    weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${night.localDate}T12:00:00Z`))

  return <article className="night-card">
    <p className="night-index">{index === 0 ? 'Tonight' : `Night ${index + 1}`}</p>
    <h3>{date} <span className="utc-offset">(UTC{night.utcOffsetAtStart})</span></h3>
    <span className="unknown-badge">Insufficient data</span>
    <p>{night.reason}</p>
  </article>
}
