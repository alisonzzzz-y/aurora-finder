import test from 'node:test'
import assert from 'node:assert/strict'
import { stormAlerts } from '../src/components/aurora/stormAlerts.ts'
const now = Date.parse('2026-10-13T12:30:00Z')
const record = (periodStart, kp, type = 'OBSERVED') => ({periodStart,kp,type})
const data = records => ({retrievedAt:new Date(now).toISOString(),records})
test('only current observed activity and the next qualifying future forecast trigger', () => {
  const result = stormAlerts(data([
    record('2026-10-13T06:00:00Z',8),
    record('2026-10-13T12:00:00Z',5.33),
    record('2026-10-13T15:00:00Z',4,'PREDICTED'),
    record('2026-10-13T18:00:00Z',6,'PREDICTED'),
    record('2026-10-13T21:00:00Z',7,'PREDICTED'),
  ]),now)
  assert.deepEqual(result.map(r=>[r.kind,r.kp]),[['current',5.33],['forecast',6]])
})
test('stale, invalid and expired readings cannot trigger', () => {
  assert.deepEqual(stormAlerts({...data([record('2026-10-13T12:00:00Z',8)]),retrievedAt:'2026-10-13T11:00:00Z'},now),[])
  assert.deepEqual(stormAlerts(data([record('invalid',6),record('2026-10-13T12:00:00Z',NaN),record('2026-10-13T12:00:00Z',10),record('2026-10-13T09:00:00Z',7)]),now),[])
})
test('observed correction overrides an estimate and low values do not alert', () => {
  assert.deepEqual(stormAlerts(data([record('2026-10-13T12:00:00Z',6,'ESTIMATED'),record('2026-10-13T12:00:00Z',4.67)]),now),[])
})
test('notification identity changes with severity, not minor revisions', () => {
  const key = kp=>stormAlerts(data([record('2026-10-13T12:00:00Z',kp)]),now)[0].key
  assert.equal(key(5),key(5.67))
  assert.notEqual(key(5.67),key(6))
})
