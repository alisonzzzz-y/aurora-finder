import type { AuroraMapPoint } from '../../types/auroraMap'

function distanceKm(a: AuroraMapPoint, b: AuroraMapPoint) {
  const radians = Math.PI / 180
  const latA = a.latitude * radians
  const latB = b.latitude * radians
  const latDelta = (b.latitude - a.latitude) * radians
  let longitudeDelta = (b.longitude - a.longitude) * radians
  if (longitudeDelta > Math.PI) longitudeDelta -= Math.PI * 2
  if (longitudeDelta < -Math.PI) longitudeDelta += Math.PI * 2
  const haversine = Math.sin(latDelta / 2) ** 2
    + Math.cos(latA) * Math.cos(latB) * Math.sin(longitudeDelta / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

export function strongestDistinctPoints(points: AuroraMapPoint[]) {
  const ranked = [...points].sort((a, b) => b.auroraValue - a.auroraValue)
  const selected: AuroraMapPoint[] = []
  for (const point of ranked) {
    if (selected.every(previous => distanceKm(previous, point) >= 700)) selected.push(point)
    if (selected.length === 3) break
  }
  return selected
}
