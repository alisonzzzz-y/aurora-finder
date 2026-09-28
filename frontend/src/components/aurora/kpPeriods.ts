import type { KpIndexRecord } from '../../types/kpIndex'

export function selectKpPeriods(records: KpIndexRecord[], now = Date.now()) {
  const sorted = [...records].sort((left, right) => Date.parse(left.periodStart) - Date.parse(right.periodStart))
  const reported = sorted.filter(record => record.type !== 'PREDICTED' && Date.parse(record.periodStart) <= now)
  // NOAA can publish future slots as ESTIMATED before switching to PREDICTED.
  // Keep the source's type rather than removing those available time slots.
  const upcoming = sorted.filter(record => record.type === 'PREDICTED'
    ? Date.parse(record.periodStart) >= now
    : Date.parse(record.periodStart) > now)
  return {
    reported: reported.at(-1),
    forecast: upcoming[0],
    trend: [...reported.slice(-8), ...upcoming.slice(0, 8)],
  }
}

export function canConnectKpPeriods(left: KpIndexRecord, right: KpIndexRecord) {
  const interval = Date.parse(right.periodStart) - Date.parse(left.periodStart)
  return interval > 0 && interval <= 4 * 60 * 60 * 1000
}
