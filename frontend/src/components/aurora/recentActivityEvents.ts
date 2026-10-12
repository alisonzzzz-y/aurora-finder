import type { KpIndexRecord } from '../../types/kpIndex'

export type ActivityEvent = { start: string; end: string; peak: number; estimated: boolean }
/** Completed source intervals only. Forecasts never become historical observations. */
export function recentActivityEvents(records: KpIndexRecord[], now: number): ActivityEvent[] {
  const byTime = new Map<number, KpIndexRecord>()
  for (const record of records) {
    const at = Date.parse(record.periodStart)
    if (!Number.isFinite(at) || at < now - 72 * 3600000 || at + 10800000 > now || !Number.isFinite(record.kp)
      || record.kp < 0 || record.kp > 9 || !['OBSERVED', 'ESTIMATED'].includes(record.type)) continue
    const previous = byTime.get(at)
    if (!previous || record.type === 'OBSERVED') byTime.set(at, record)
  }
  const events: ActivityEvent[] = []
  for (const [at, record] of [...byTime].sort(([a], [b]) => a - b)) {
    if (record.kp < 5) continue
    const last = events.at(-1)
    if (last && Date.parse(last.end) === at) {
      last.end = new Date(at + 10800000).toISOString()
      last.peak = Math.max(last.peak, record.kp)
      last.estimated ||= record.type === 'ESTIMATED'
    } else events.push({ start: record.periodStart, end: new Date(at + 10800000).toISOString(), peak: record.kp, estimated: record.type === 'ESTIMATED' })
  }
  return events.reverse().slice(0, 3)
}
