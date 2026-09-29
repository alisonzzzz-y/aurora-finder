export type ForecastCoveragePoint = {
  validAt: string
  cloudCoverPercent: number | null
}

/** Return cloud observations whose timestamps fall within the inclusive aurora forecast window. */
export function cloudPointsInsideAuroraWindow(
  points: ForecastCoveragePoint[],
  startUtc: string | null,
  endUtc: string | null,
): ForecastCoveragePoint[] {
  if (!startUtc || !endUtc) return []
  const start = Date.parse(startUtc)
  const end = Date.parse(endUtc)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) return []

  return points
    .filter(point => {
      const instant = Date.parse(point.validAt)
      return point.cloudCoverPercent !== null && Number.isFinite(instant)
        && instant >= start && instant <= end
    })
    .sort((a, b) => Date.parse(a.validAt) - Date.parse(b.validAt))
}
