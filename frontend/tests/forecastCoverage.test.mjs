import assert from 'node:assert/strict'
import test from 'node:test'
import { cloudPointsInsideAuroraWindow } from '../src/utils/forecastCoverage.ts'

test('includes cloud readings at both forecast window boundaries', () => {
  const start = { validAt: '2026-09-29T09:35:00Z', cloudCoverPercent: 20 }
  const inside = { validAt: '2026-09-29T10:00:00Z', cloudCoverPercent: 50 }
  const end = { validAt: '2026-09-29T11:01:00Z', cloudCoverPercent: 80 }
  const outside = { validAt: '2026-09-29T11:02:00Z', cloudCoverPercent: 90 }

  assert.deepEqual(
    cloudPointsInsideAuroraWindow([end, outside, start, inside],
      '2026-09-29T09:35:00Z', '2026-09-29T11:01:00Z'),
    [start, inside, end],
  )
})

test('excludes readings without cloud values', () => {
  const missing = { validAt: '2026-09-29T10:00:00Z', cloudCoverPercent: null }
  assert.deepEqual(
    cloudPointsInsideAuroraWindow([missing], '2026-09-29T09:00:00Z', '2026-09-29T11:00:00Z'),
    [],
  )
})

test('returns no matches for missing, invalid, or reversed forecast windows', () => {
  const points = [{ validAt: '2026-09-29T10:00:00Z', cloudCoverPercent: 50 }]
  assert.deepEqual(cloudPointsInsideAuroraWindow(points, null, '2026-09-29T11:00:00Z'), [])
  assert.deepEqual(cloudPointsInsideAuroraWindow(points, 'invalid', '2026-09-29T11:00:00Z'), [])
  assert.deepEqual(cloudPointsInsideAuroraWindow(points, '2026-09-29T11:00:00Z', '2026-09-29T10:00:00Z'), [])
})
