import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeKpRecords, nearestKpRecord, formatKpTime, KP_PERIOD_MS } from '../src/components/aurora/kpChartData.ts'
const record = (periodStart, kp = 2, type = 'OBSERVED') => ({periodStart,kp,type,noaaScale:null,activityLevel:'LOW'})

test('chart sorts valid source readings and prefers observations over duplicate predictions', () => {
  const observed=record('2026-10-11T03:00:00Z',3)
  const earlier=record('2026-10-11T00:00:00Z')
  const source=[observed,record(observed.periodStart,4,'PREDICTED'),record('invalid'),record('2026-10-11T06:00:00Z',10),earlier]
  assert.deepEqual(normalizeKpRecords(source),[earlier,observed])
  assert.equal(source.length,5)
})

test('hover snaps to actual readings and does not invent a value in a missing period', () => {
  const first=record('2026-10-11T00:00:00Z'), last=record('2026-10-11T09:00:00Z')
  const records=[first,last], start=Date.parse(first.periodStart)
  assert.equal(nearestKpRecord(records,start+KP_PERIOD_MS/4),first)
  assert.equal(nearestKpRecord(records,start+KP_PERIOD_MS),undefined)
  assert.equal(nearestKpRecord(records,Date.parse(last.periodStart)),last)
})

test('time zone formatting keeps the source instant and changes calendar dates across zones', () => {
  const instant='2026-10-10T23:00:00Z'
  assert.match(formatKpTime(instant,'zh-CN','UTC',true),/10月10日.*23:00/)
  assert.match(formatKpTime(instant,'zh-CN','Asia/Shanghai',true),/10月11日.*07:00/)
  assert.match(formatKpTime(instant,'en','Europe/Dublin'),/Oct 11.*00:00.*GMT\+1/)
  assert.match(formatKpTime('2026-10-25T02:00:00Z','en','Europe/Dublin'),/Oct 25.*02:00.*GMT/)
})
