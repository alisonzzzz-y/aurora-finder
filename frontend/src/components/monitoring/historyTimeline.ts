export const QUARTER_HOUR = 15 * 60000
export const quarterHour = (at: string | number) => Math.floor((typeof at === 'string' ? Date.parse(at) : at) / QUARTER_HOUR) * QUARTER_HOUR
export function historyScale(values: number[]) {
  const valid=values.filter(v=>Number.isFinite(v) && v>=0 && v<=100)
  return Math.min(100,Math.max(10,Math.ceil(Math.max(0,...valid)/10)*10))
}
export function historyDays(start: number, end: number) {
  if (!Number.isFinite(start) || !Number.isFinite(end)) return []
  const day=86400000, first=Math.floor(start/day)*day, last=Math.floor(end/day)*day
  return Array.from({length:Math.min(8,Math.max(0,(last-first)/day+1))},(_,i)=>first+i*day)
}
