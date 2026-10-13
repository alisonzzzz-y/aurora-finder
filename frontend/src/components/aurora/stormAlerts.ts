import type { KpIndexData } from '../../types/kpIndex'

export type StormAlert = { key: string; kind: 'current' | 'forecast'; kp: number; start: string; end: string }
const HOUR = 3600000

// Old cached data must never trigger a new notification.
export function stormAlerts(data: KpIndexData, now: number): StormAlert[] {
  const age = now - Date.parse(data.retrievedAt)
  if (!Number.isFinite(age) || age < -60000 || age > 30 * 60000) return []
  const records = data.records.filter(r => Number.isFinite(r.kp) && r.kp >= 0 && r.kp <= 9 && Number.isFinite(Date.parse(r.periodStart)))
  const current = records.filter(r => r.type !== 'PREDICTED' && Date.parse(r.periodStart) <= now && Date.parse(r.periodStart) + 3 * HOUR > now)
    .sort((a,b) => Date.parse(b.periodStart)-Date.parse(a.periodStart) || Number(b.type === 'OBSERVED')-Number(a.type === 'OBSERVED'))[0]
  const forecast = records.filter(r => r.type === 'PREDICTED' && r.kp >= 5 && Date.parse(r.periodStart) > now && Date.parse(r.periodStart) <= now + 72 * HOUR)
    .sort((a,b) => Date.parse(a.periodStart)-Date.parse(b.periodStart))[0]
  return ([['current', current], ['forecast', forecast]] as const).flatMap(([kind,r]) => r && r.kp >= 5 ? [{
    key: `${kind}:${r.periodStart}:${Math.floor(r.kp)}`, kind, kp: r.kp, start: r.periodStart,
    end: new Date(Date.parse(r.periodStart)+3*HOUR).toISOString(),
  }] : [])
}

export function freshStormData(data: KpIndexData, now: number): boolean {
  const age = now - Date.parse(data.retrievedAt)
  return Number.isFinite(age) && age >= -60000 && age <= 30 * 60000
}
