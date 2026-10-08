import test from 'node:test'
import assert from 'node:assert/strict'
import { dailyForecastPeaks } from '../src/components/aurora/dailyForecastPeaks.ts'
const point = (periodStart, kp, type = 'PREDICTED') => ({periodStart, kp, type})
test('daily peaks exclude past periods and observations, retain ongoing predictions', () => {
 const data = [point('2026-10-08T00:00:00Z',9),point('2026-10-08T12:00:00Z',4),point('2026-10-08T15:00:00Z',3),point('2026-10-09T00:00:00Z',5),point('2026-10-09T03:00:00Z',9,'OBSERVED'),point('invalid',8)]
 assert.deepEqual(dailyForecastPeaks(data,Date.parse('2026-10-08T13:00:00Z')).map(p=>p.kp),[4,5])
 assert.equal(data.length,6)
})
test('ties choose earlier period and output is limited to three chronological dates', () => {
 const data=[point('2026-10-09T09:00:00Z',3),point('2026-10-09T06:00:00Z',3),point('2026-10-08T12:00:00Z',2),point('2026-10-10T12:00:00Z',4),point('2026-10-11T12:00:00Z',7)]
 assert.deepEqual(dailyForecastPeaks(data,Date.parse('2026-10-08T12:00:00Z')).map(p=>p.periodStart),['2026-10-08T12:00:00Z','2026-10-09T06:00:00Z','2026-10-10T12:00:00Z'])
})
