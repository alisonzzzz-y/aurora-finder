import type { NightOutlook } from '../../types/outlook'

export function localNightPosition(instant: string, night: NightOutlook, timezone: string) {
  const time = Date.parse(instant)
  if (time <= Date.parse(night.evaluationWindowStartUtc)) return 0
  if (time >= Date.parse(night.evaluationWindowEndUtc)) return 24
  const parts = new Intl.DateTimeFormat('en', { timeZone: timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(time))
  const hour = Number(parts.find(part => part.type === 'hour')?.value)
  const minute = Number(parts.find(part => part.type === 'minute')?.value)
  return (hour < 12 ? hour + 12 : hour - 12) + minute / 60
}

export function darknessHours(intervals: { startUtc: string; endUtc: string }[]) {
  return intervals.reduce((total, interval) => total + Math.max(0, Date.parse(interval.endUtc) - Date.parse(interval.startUtc)), 0) / 3600000
}
