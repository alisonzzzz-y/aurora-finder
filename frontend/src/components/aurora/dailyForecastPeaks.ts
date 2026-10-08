import type { KpIndexRecord } from '../../types/kpIndex'

/** Daily peaks among still-relevant three-hour predictions; days are grouped in UTC. */
export function dailyForecastPeaks(records: KpIndexRecord[], now: number): KpIndexRecord[] {
  const peaks = new Map<string, KpIndexRecord>()
  for (const record of records) {
    if (record.type !== 'PREDICTED' || !Number.isFinite(record.kp) || Date.parse(record.periodStart) + 10800000 <= now || !Number.isFinite(Date.parse(record.periodStart))) continue
    const day = record.periodStart.slice(0, 10)
    const previous = peaks.get(day)
    if (!previous || record.kp > previous.kp || (record.kp === previous.kp && record.periodStart < previous.periodStart)) peaks.set(day, record)
  }
  return [...peaks.entries()].sort(([a], [b]) => a.localeCompare(b)).slice(0, 3).map(([, value]) => value)
}
