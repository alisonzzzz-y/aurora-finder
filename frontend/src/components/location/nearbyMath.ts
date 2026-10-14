import type { Location } from '../../types/location'
import type { ObservationFacts } from '../../types/observationFacts'
type Point = Pick<Location, 'latitude' | 'longitude'>
export function distanceKm(a: Point, b: Point) {
  const rad = Math.PI / 180
  const dlat = (b.latitude-a.latitude)*rad, dlon = (b.longitude-a.longitude)*rad
  const h = Math.sin(dlat/2)**2+Math.cos(a.latitude*rad)*Math.cos(b.latitude*rad)*Math.sin(dlon/2)**2
  return 6371*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,h))))
}
export function filterNearby(origin: Location, places: Location[], radius: number) {
  return [...new Map(places.filter(p=>p.id!==origin.id && distanceKm(origin,p)>.5 && distanceKm(origin,p)<=radius).map(p=>[p.id,p])).values()]
    .sort((a,b)=>distanceKm(origin,a)-distanceKm(origin,b)).slice(0,3)
}
export function commonCloudTimes(facts: ObservationFacts[], now: number) {
  if (!facts.length) return []
  const times = facts.map(f=>new Set(f.cloudForecast.status==='CURRENT' && f.cloudForecast.data && Date.parse(f.cloudForecast.data.expiresAt)>now
    ? f.cloudForecast.data.cloudForecast.filter(p=>p.cloudCoverPercent!==null && Number.isFinite(p.cloudCoverPercent) && p.cloudCoverPercent>=0 && p.cloudCoverPercent<=100 && Date.parse(p.validAt)>=now && Date.parse(p.validAt)<=now+24*3600000).map(p=>Date.parse(p.validAt)) : []))
  return [...times[0]].filter(t=>times.every(set=>set.has(t))).sort((a,b)=>a-b)
}
export function darknessAt(facts: ObservationFacts, time: number): boolean | null {
  const night = facts.outlook.nights.find(n=>Date.parse(n.evaluationWindowStartUtc)<=time && time<Date.parse(n.evaluationWindowEndUtc))
  const threshold = night?.solarDarkness?.thresholds.find(t=>t.threshold==='ASTRONOMICAL_TWILIGHT')
  if (!threshold || threshold.status==='CALCULATION_FAILED') return null
  return threshold.status==='CONTINUOUS' || threshold.intervals.some(i=>Date.parse(i.startUtc)<=time && time<Date.parse(i.endUtc))
}
