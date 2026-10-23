export const QUARTER_HOUR = 15 * 60000
export const quarterHour = (at: string | number) => Math.floor((typeof at === 'string' ? Date.parse(at) : at) / QUARTER_HOUR) * QUARTER_HOUR
export function historyScale(values: number[]) {
  const valid=values.filter(v=>Number.isFinite(v) && v>=0 && v<=100)
  const peak = Math.max(1, ...valid)
  const magnitude = 10 ** Math.floor(Math.log10(peak))
  const step = [1, 2, 5, 10].find(value => value * magnitude >= peak) ?? 10
  return Math.min(100, step * magnitude)
}
export function historyDays(start: number, end: number) {
  if (!Number.isFinite(start) || !Number.isFinite(end)) return []
  const day=86400000, first=Math.floor(start/day)*day, last=Math.floor(end/day)*day
  return Array.from({length:Math.min(8,Math.max(0,(last-first)/day+1))},(_,i)=>first+i*day)
}
