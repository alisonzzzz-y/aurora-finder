import type { KpIndexRecord } from '../../types/kpIndex'

export const KP_PERIOD_MS = 3 * 60 * 60 * 1000
export const KP_WINDOW_MS = 15 * KP_PERIOD_MS

export function normalizeKpRecords(records: KpIndexRecord[]) {
  const byTime = new Map<number, KpIndexRecord>()
  const priority = { OBSERVED: 3, ESTIMATED: 2, PREDICTED: 1 }
  for (const record of records) {
    const time = Date.parse(record.periodStart)
    if (!Number.isFinite(time) || !Number.isFinite(record.kp) || record.kp < 0 || record.kp > 9) continue
    const previous = byTime.get(time)
    if (!previous || priority[record.type] >= priority[previous.type]) byTime.set(time, record)
  }
  return [...byTime.entries()].sort(([left], [right]) => left - right).map(([, record]) => record)
}

export function nearestKpRecord(records: KpIndexRecord[], instant: number) {
  let nearest: KpIndexRecord | undefined
  let distance = KP_PERIOD_MS / 2
  for (const record of records) {
    const delta = Math.abs(Date.parse(record.periodStart) - instant)
    if (delta <= distance) { distance = delta; nearest = record }
  }
  return nearest
}

export function formatKpTime(instant: string | number, locale: string, timeZone: string, compact = false) {
  return new Intl.DateTimeFormat(locale, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    timeZone, ...(compact ? {} : { year: 'numeric', timeZoneName: 'shortOffset' as const }),
  }).format(new Date(instant))
}

// NOAA rounds Kp to a whole K index before assigning a G level, so 4.67 (5-) is already G1.
export function kpStormLevel(kp: number) {
  return Math.max(0, Math.min(5, Math.round(kp) - 4))
}

export function kpStormScale(kp: number, names: string[], belowG1: string, scale?: string) {
  if (scale === 'BELOW_G1' || kpStormLevel(kp) === 0) return belowG1
  const level = scale?.startsWith('G') ? Number(scale.slice(1)) : kpStormLevel(kp)
  if (!Number.isFinite(level) || level < 1 || level > 5) return belowG1
  return `G${level} · ${names[level - 1]}`
}

// G3 colour includes higher storms; the readout retains the exact G level.
export function kpStormBand(kp: number) {
  const level = kpStormLevel(kp)
  return level >= 3 ? 'kp-storm-g3' : level === 2 ? 'kp-storm-g2' : level === 1 ? 'kp-storm-g1' : ''
}
