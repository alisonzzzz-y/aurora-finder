import test from 'node:test'
import assert from 'node:assert/strict'
import { canConnectKpPeriods, selectKpPeriods } from '../src/components/aurora/kpPeriods.ts'

const now = Date.parse('2026-09-28T14:00:00Z')
function record(hour, type = 'ESTIMATED') {
  return {
    periodStart: new Date(Date.parse('2026-09-28T00:00:00Z') + hour * 3600000).toISOString(),
    kp: 1.67, type, noaaScale: null, activityLevel: 'LOW',
  }
}

test('retains future estimates between the latest reported value and predictions', () => {
  const source = [record(9, 'OBSERVED'), record(12), record(15), record(18), record(21), record(24, 'PREDICTED')]
  const periods = selectKpPeriods(source, now)
  assert.deepEqual(periods.trend, source)
  assert.equal(periods.reported, source[1])
  assert.equal(periods.forecast, source[2])
  assert.deepEqual(periods.trend.map(item => item.type), ['OBSERVED', 'ESTIMATED', 'ESTIMATED', 'ESTIMATED', 'ESTIMATED', 'PREDICTED'])
  assert.ok(periods.trend.slice(1).every((item, i) => canConnectKpPeriods(periods.trend[i], item)))
})

test('does not draw a line across genuinely missing three-hour periods', () => {
  const periods = selectKpPeriods([record(12), record(24, 'PREDICTED')], now)
  assert.equal(canConnectKpPeriods(...periods.trend), false)
  assert.equal(canConnectKpPeriods(record(12), record(12)), false)
})

test('sorts mixed input without changing the source and bounds each side of the chart', () => {
  const source = Array.from({ length: 30 }, (_, i) => record(i * 3, i < 6 ? 'OBSERVED' : 'PREDICTED')).reverse()
  const original = [...source]
  const periods = selectKpPeriods(source, Date.parse(record(18).periodStart))
  assert.deepEqual(source, original)
  assert.equal(periods.trend.length, 14)
  assert.equal(periods.forecast.periodStart, record(18).periodStart)
  assert.ok(periods.trend.slice(1).every((item, i) => Date.parse(item.periodStart) > Date.parse(periods.trend[i].periodStart)))
})

test('does not duplicate the estimate at the exact current interval boundary', () => {
  const source = [record(12), record(15), record(18, 'PREDICTED')]
  const periods = selectKpPeriods(source, Date.parse(record(15).periodStart))
  assert.deepEqual(periods.trend, source)
  assert.equal(periods.reported, source[1])
  assert.equal(periods.forecast, source[2])
})
